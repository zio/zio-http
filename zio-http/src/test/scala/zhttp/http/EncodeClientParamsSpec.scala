package zhttp.http

import io.netty.handler.codec.http.{HttpHeaderNames, HttpVersion}
import zhttp.internal.HttpGen
import zhttp.service.{Client, EncodeClientParams}
import zio.random.Random
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
    testM("method") {
      check(anyClientParam) { params =>
        val req = encodeClientParams(HttpVersion.HTTP_1_1, params)
        assert(req.method())(equalTo(params.method.asHttpMethod))
      }
    } +
      testM("method on HttpData.File") {
        check(HttpGen.clientParamsForFileHttpData()) { params =>
          val req = encodeClientParams(HttpVersion.HTTP_1_1, params)
          assert(req.method())(equalTo(params.method.asHttpMethod))
        }
      } +
      testM("uri") {
        check(anyClientParam) { params =>
          val req = encodeClientParams(HttpVersion.HTTP_1_1, params)
          assert(req.uri())(equalTo(params.url.asString))
        }
      } +
      testM("uri on HttpData.File") {
        check(HttpGen.clientParamsForFileHttpData()) { params =>
          val req = encodeClientParams(HttpVersion.HTTP_1_1, params)
          assert(req.uri())(equalTo(params.url.asString))
        }
      } +
      testM("content-length") {
        check(clientParamWithFiniteData(5)) { params =>
          val req = encodeClientParams(HttpVersion.HTTP_1_1, params)
          assert(req.headers().getInt(HttpHeaderNames.CONTENT_LENGTH).toLong)(equalTo(5L))
        }
      } +
      testM("host header from Absolute url") {
        check(anyClientParam) { params =>
          val req = encodeClientParams(HttpVersion.HTTP_1_1, params)
          params.url.host match {
            case Some(value) => assert(req.headers().get(HttpHeaderNames.HOST))(equalTo(value))
            case None        => assert(req.headers().contains(HttpHeaderNames.HOST))(isFalse)
          }
        }
      }
  }
}
