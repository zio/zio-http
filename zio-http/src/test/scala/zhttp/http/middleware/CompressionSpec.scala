package zhttp.http.middleware

import io.netty.buffer.ByteBuf
import zhttp.http.Middleware.{parseAcceptEncodingHeaders, serveCompressed}
import zhttp.http._
import zhttp.internal.HttpAppTestExtensions
import zio.test.Assertion.{equalTo, isNone, isSome, isTrue}
import zio.test._

object CompressionSpec extends DefaultRunnableSpec with HttpAppTestExtensions {
  private def checkHeader(assert: Assertion[Option[String]]) =
    Assertion.hasField("header", (response: Response) => response.headers.contentEncoding.map(_.toString()), assert)

  private val noEncodingheader = checkHeader(isNone)
  private val hasGzipHeader    = checkHeader(isSome(equalTo("gzip")))
  private val hasBrHeader      = checkHeader(isSome(equalTo("br")))

  private def hasBody(expected: String) =
    Assertion.hasField[ByteBuf, String]("body", (body: ByteBuf) => body.toString(HTTP_CHARSET), equalTo(expected))

  override def spec = suite("CompressionMiddlewares") {
    val originalReq = Request(
      method = Method.GET,
      url = URL(!! / "file.js"),
    )
    suite("parse accept encoding header") {
      test("Extract encoding from a single value") {
        assert(parseAcceptEncodingHeaders(HeaderValues.gzip))(
          equalTo(
            List(
              HeaderValues.gzip.toString -> 1.0,
            ),
          ),
        )
      } +
        test("Return an empty list in case of an empty header") {
          assert(parseAcceptEncodingHeaders(""))(equalTo(List()))
        } +
        test("Parse multiple headers") {
          assert(parseAcceptEncodingHeaders(s"${HeaderValues.gzip},   ${HeaderValues.br}").toSet)(
            equalTo(
              Set(
                HeaderValues.gzip.toString -> 1.0,
                HeaderValues.br.toString   -> 1.0,
              ),
            ),
          )
        } + test("Sort multiple headers") {
          assert(parseAcceptEncodingHeaders(s"${HeaderValues.gzip};q=0.8, ${HeaderValues.br}"))(
            equalTo(
              List(
                HeaderValues.br.toString   -> 1.0,
                HeaderValues.gzip.toString -> 0.8,
              ),
            ),
          )
        }
    } +
      suite("no GZIP server support") {
        val app = Http.collectHttp[Request] { case req => Http.text(req.path.toString()) }
        testM("Request with GZIP support") {
          val request  = originalReq.copy(headers = Headers.acceptEncoding(HeaderValues.gzip))
          val expected = "/file.js"

          for {
            res  <- app(request)
            body <- res.data.toByteBuf
          } yield assert(body)(hasBody(expected)) &&
            assert(res)(noEncodingheader)

        } + testM("Fall back to uncompressed assets") {
          var called = false
          val app    = Http.collectHttp[Request] {
            case req if req.path.toString.endsWith(".js") => Http.text(req.path.toString())
            case req if req.path.toString.endsWith(".gz") =>
              called = true
              Http.notFound
          } @@ serveCompressed(CompressionFormat.Gzip())

          val request  = originalReq
            .copy(headers = Headers.acceptEncoding(HeaderValues.gzip))
          val expected = "/file.js"

          for {
            res  <- app(request)
            body <- res.data.toByteBuf
          } yield assert(res)(noEncodingheader) &&
            assert(body)(hasBody(expected)) && assert(called)(isTrue)
        }
      } +
      testM("Fall back to uncompressed assets if http app is not defined") {
        // The following app is used to simulate a HTTP app that would not give any response for a compressed response (not even a 404)
        val app = Http.collectHttp[Request] {
          case req if req.path.toString.endsWith(".js") => Http.text(req.path.toString())
        } @@ serveCompressed(CompressionFormat.Gzip())

        val request  = originalReq
          .copy(headers = Headers.acceptEncoding(HeaderValues.gzip))
        val expected = "/file.js"

        for {
          res  <- app(request)
          body <- res.data.toByteBuf
        } yield assert(res)(noEncodingheader) &&
          assert(body)(hasBody(expected))
      } +
      suite("GZIP server support") {
        val echo = Http.collectHttp[Request] { case req => Http.text(req.path.toString()) }
        val app  = echo @@ serveCompressed(CompressionFormat.Gzip())

        testM("Request without GZIP support") {
          val request  = originalReq
          val expected = "/file.js"

          for {
            res  <- app(request)
            body <- res.data.toByteBuf
          } yield assert(body)(hasBody(expected)) &&
            assert(res.headers.contentEncoding)(isNone)
        } +
          testM("Request with GZIP support") {
            val request  = originalReq.copy(headers = Headers.acceptEncoding(HeaderValues.gzip))
            val expected = "/file.js.gz"

            for {
              res  <- app(request)
              body <- res.data.toByteBuf
            } yield assert(body)(hasBody(expected)) &&
              assert(res)(hasGzipHeader)
          } +
          testM("Request with GZIP and BR support") {
            val request  =
              originalReq.copy(headers = Headers.acceptEncoding(s"${HeaderValues.gzip}, ${HeaderValues.br}"))
            val expected = "/file.js.gz"

            for {
              res  <- app(request)
              body <- res.data.toByteBuf
            } yield assert(body)(hasBody(expected)) &&
              assert(res)(hasGzipHeader)
          } +
          testM("Request with BR support") {
            val request  = originalReq.copy(headers = Headers.acceptEncoding(HeaderValues.br))
            val expected = "/file.js"

            for {
              res  <- app(request)
              body <- res.data.toByteBuf
            } yield assert(body)(hasBody(expected)) &&
              assert(res)(noEncodingheader)
          }
      } +
      suite("GZIP + BR server support") {
        testM("Try BR before GZ") {
          // we want to check that the br endpoint was called once
          var called = false

          val app = Http.collectHttp[Request] {
            case req if req.path.toString.endsWith(".gz") => Http.text(req.path.toString())
            case req if req.path.toString.endsWith(".br") =>
              called = true
              Http.notFound
          } @@ serveCompressed(Set[CompressionFormat](CompressionFormat.Brotli(), CompressionFormat.Gzip()))

          val request = originalReq
            .copy(headers = Headers.acceptEncoding(s"${HeaderValues.gzip};q=0.4, ${HeaderValues.br};q=0.5"))

          val expected = "/file.js.gz"

          for {
            res  <- app(request)
            body <- res.data.toByteBuf
          } yield assert(res)(hasGzipHeader) &&
            assert(body)(hasBody(expected)) && assert(called)(isTrue)
        } +
          testM("Return BR if available") {
            val app = Http.collectHttp[Request] {
              case req if req.path.toString.endsWith(".br") => Http.text(req.path.toString())
            } @@ serveCompressed(Set[CompressionFormat](CompressionFormat.Brotli(), CompressionFormat.Gzip()))

            val request = originalReq
              .copy(headers = Headers.acceptEncoding(s"${HeaderValues.gzip};q=0.4, ${HeaderValues.br};q=0.5"))

            val expected = "/file.js.br"

            for {
              res  <- app(request)
              body <- res.data.toByteBuf
            } yield assert(res)(hasBrHeader) && assert(body)(hasBody(expected))
          }
      }
  }
}
