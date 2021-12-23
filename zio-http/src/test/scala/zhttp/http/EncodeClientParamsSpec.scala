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

  val clientParamWithFiniteData: Gen[Random with Sized, Client.ClientParams] = HttpGen.clientParams(
    for {
      content <- Gen.alphaNumericString
      byteBuf <- Gen.fromEffect(HttpData.fromText(content).toByteBuf)
      data    <- Gen.fromIterable(List(HttpData.fromByteBuf(byteBuf)))
    } yield data,
  )

  def spec = suite("EncodeClientParams") {
    testM("method") {
      check(anyClientParam) { params =>
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
      testM("content-length") {
        check(clientParamWithFiniteData) { params =>
          val req = encodeClientParams(HttpVersion.HTTP_1_1, params)
          assert(req.headers().getInt(HttpHeaderNames.CONTENT_LENGTH).toLong)(equalTo(params.data.unsafeSize))
        }
      }
  }
}
