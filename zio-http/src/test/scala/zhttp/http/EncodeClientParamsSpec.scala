package zhttp.http

import io.netty.handler.codec.http.{HttpHeaderNames, HttpVersion}
import zhttp.internal.HttpGen
import zhttp.service.{Client, EncodeClientParams}
import zio.Random
import zio.test.Assertion._
import zio.test._

object EncodeClientParamsSpec extends DefaultRunnableSpec with EncodeClientParams {

  val anyClientParam: Gen[Random with Sized, Client.ClientParams] = HttpGen.clientParams(
    HttpGen.httpData(
      Gen.listOf(Gen.alphaNumericString),
    ),
  )

  def clientParamWithFiniteData(size: Int): Gen[Random with Sized, Client.ClientParams] = HttpGen.clientParams(
    for {
      content <- Gen.alphaNumericStringBounded(size, size)
      data    <- Gen.fromIterable(List(HttpData.fromString(content)))
    } yield data,
  )

  def spec = suite("EncodeClientParams") {
    test("method") {
      check(anyClientParam) { params =>
        val req = encodeClientParams(HttpVersion.HTTP_1_1, params)
        assert(req.method())(equalTo(params.method.asHttpMethod))
      }
    } +
      test("method on HttpData.File") {
        check(HttpGen.clientParamsForFileHttpData()) { params =>
          val req = encodeClientParams(HttpVersion.HTTP_1_1, params)
          assert(req.method())(equalTo(params.method.asHttpMethod))
        }
      } +
      test("uri") {
        check(anyClientParam) { params =>
          val req = encodeClientParams(HttpVersion.HTTP_1_1, params)
          assert(req.uri())(equalTo(params.url.asString))
        }
      } +
      test("uri on HttpData.File") {
        check(HttpGen.clientParamsForFileHttpData()) { params =>
          val req = encodeClientParams(HttpVersion.HTTP_1_1, params)
          assert(req.uri())(equalTo(params.url.asString))
        }
      } +
      test("content-length") {
        check(clientParamWithFiniteData(5)) { params =>
          val req = encodeClientParams(HttpVersion.HTTP_1_1, params)
          assert(req.headers().getInt(HttpHeaderNames.CONTENT_LENGTH).toLong)(equalTo(5L))
        }
      }
  }
}
