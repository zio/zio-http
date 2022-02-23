package zhttp.http

import io.netty.handler.codec.http.HttpHeaderNames
import zhttp.internal.HttpGen
import zhttp.service.{Client, EncodeClientRequest}
import zio._
import zio.test.Assertion._
import zio.test._

object EncodeClientRequestSpec extends DefaultRunnableSpec with EncodeClientRequest {

  val anyClientParam: Gen[Random with Sized, Client.ClientRequest] = HttpGen.clientRequest(
    HttpGen.httpData(
      Gen.listOf(Gen.alphaNumericString),
    ),
  )

  val clientParamWithAbsoluteUrl = HttpGen.clientRequest(
    dataGen = HttpGen.httpData(
      Gen.listOf(Gen.alphaNumericString),
    ),
    urlGen = HttpGen.genAbsoluteURL,
  )

  def clientParamWithFiniteData(size: Int): Gen[Random with Sized, Client.ClientRequest] = HttpGen.clientRequest(
    for {
      content <- Gen.alphaNumericStringBounded(size, size)
      data    <- Gen.fromIterable(List(HttpData.fromString(content)))
    } yield data,
  )

  def spec = suite("EncodeClientParams") {
    test("method") {
      check(anyClientParam) { params =>
        val req = encode(params).map(_.method())
        assertM(req)(equalTo(params.method.toJava))
      }
    } +
      test("method on HttpData.RandomAccessFile") {
        check(HttpGen.clientParamsForFileHttpData()) { params =>
          val req = encode(params).map(_.method())
          assertM(req)(equalTo(params.method.toJava))
        }
      } +
      suite("uri") {
        test("uri") {
          check(anyClientParam) { params =>
            val req = encode(params).map(_.uri())
            assertM(req)(equalTo(params.url.relative.encode))
          }
        } +
          test("uri on HttpData.File") {
            check(HttpGen.clientParamsForFileHttpData()) { params =>
              val req = encode(params).map(_.uri())
              assertM(req)(equalTo(params.url.relative.encode))
            }
          }
      } +
      test("content-length") {
        check(clientParamWithFiniteData(5)) { params =>
          val req = encode(params).map(
            _.headers().getInt(HttpHeaderNames.CONTENT_LENGTH).toLong,
          )
          assertM(req)(equalTo(5L))
        }
      } +
      test("host header") {
        check(anyClientParam) { params =>
          val req =
            encode(params).map(i => Option(i.headers().get(HttpHeaderNames.HOST)))
          assertM(req)(equalTo(params.url.host))
        }
      } +
      test("host header when absolute url") {
        check(clientParamWithAbsoluteUrl) { params =>
          val req = encode(params)
            .map(i => Option(i.headers().get(HttpHeaderNames.HOST)))
          assertM(req)(equalTo(params.url.host))
        }
      } +
      test("only one host header exists") {
        check(clientParamWithAbsoluteUrl) { params =>
          val req = encode(params)
            .map(_.headers().getAll(HttpHeaderNames.HOST).size)
          assertM(req)(equalTo(1))
        }
      } +
      test("http version") {
        check(anyClientParam) { params =>
          val req = encode(params).map(i => i.protocolVersion())
          assertM(req)(equalTo(params.version.toJava))
        }
      }
  }
}
