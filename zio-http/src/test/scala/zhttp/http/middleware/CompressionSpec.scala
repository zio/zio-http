package zhttp.http.middleware

import zhttp.http.Middleware.serveCompressed
import zhttp.http.Middleware.parseAcceptEncodingHeaders
import zhttp.http._
import zhttp.http.middleware.CompressionFormat
import zhttp.internal.HttpAppTestExtensions
import zio.test.Assertion.{equalTo, isNone, isSome}
import zio.test._

object CompressionSpec extends DefaultRunnableSpec with HttpAppTestExtensions {
  override def spec = suite("CompressionMiddlewares") {
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
          val request = Request(
            method = Method.GET,
            url = URL(!! / "file.js"),
            headers = Headers.acceptEncoding(HeaderValues.gzip),
          )

          val expected = "/file.js"

          for {
            res  <- app(request)
            body <- res.getBodyAsByteBuf
          } yield assert(body.toString(HTTP_CHARSET))(equalTo(expected)) &&
            assert(res.headers.getContentEncoding)(isNone)

        } + testM("Fall back to uncompressed assets") {
          val app = Http.collectHttp[Request] {
            case req if req.path.toString.endsWith(".js")   => Http.text(req.path.toString())
            case req if req.path.toString.endsWith(".gzip") => Http.notFound
          } @@ serveCompressed(CompressionFormat.Gzip())

          val request = Request(
            method = Method.GET,
            url = URL(!! / "file.js"),
            headers = Headers.acceptEncoding(HeaderValues.gzip),
          )

          val expected = "/file.js"

          for {
            res  <- app(request)
            body <- res.getBodyAsByteBuf
          } yield assert(body.toString(HTTP_CHARSET))(equalTo(expected)) &&
            assert(res.headers.getContentEncoding)(isNone)
        }
      } +
      suite("GZIP server support") {
        val echo = Http.collectHttp[Request] { case req => Http.text(req.path.toString()) }
        val app  = echo @@ serveCompressed(CompressionFormat.Gzip())

        testM("Request without GZIP support") {
          val request = Request(
            method = Method.GET,
            url = URL(!! / "file.js"),
          )

          val expected = "/file.js"

          for {
            res  <- app(request)
            body <- res.getBodyAsByteBuf
          } yield assert(body.toString(HTTP_CHARSET))(equalTo(expected)) &&
            assert(res.headers.getContentEncoding)(isNone)
        } +
          testM("Request with GZIP support") {
            val request = Request(
              method = Method.GET,
              url = URL(!! / "file.js"),
              headers = Headers.acceptEncoding(HeaderValues.gzip),
            )

            val expected = "/file.js.gz"

            for {
              res  <- app(request)
              body <- res.getBodyAsByteBuf
            } yield assert(body.toString(HTTP_CHARSET))(equalTo(expected)) &&
              assert(res.headers.getContentEncoding)(isSome(equalTo("gzip")))
          } +
          testM("Request with GZIP and BR support") {
            val request = Request(
              method = Method.GET,
              url = URL(!! / "file.js"),
              headers = Headers.acceptEncoding(s"${HeaderValues.gzip}, ${HeaderValues.br}"),
            )

            val expected = "/file.js.gz"

            for {
              res  <- app(request)
              body <- res.getBodyAsByteBuf
            } yield assert(body.toString(HTTP_CHARSET))(equalTo(expected)) &&
              assert(res.headers.getContentEncoding)(isSome(equalTo("gzip")))
          } +
          testM("Request with BR support") {
            val request = Request(
              method = Method.GET,
              url = URL(!! / "file.js"),
              headers = Headers.acceptEncoding(HeaderValues.br),
            )

            val expected = "/file.js"

            for {
              res  <- app(request)
              body <- res.getBodyAsByteBuf
            } yield assert(body.toString(HTTP_CHARSET))(equalTo(expected)) &&
              assert(res.headers.getContentEncoding)(isNone)
          }
      }
  }
}
