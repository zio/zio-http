package zhttp.http

import io.netty.handler.codec.http.{HttpHeaderNames, HttpVersion}
import zhttp.internal.HttpGen
import zhttp.service.EncodeClientParams
import zio.random.Random
import zio.test.Assertion._
import zio.test._

object EncodeRequestSpec extends DefaultRunnableSpec with EncodeClientParams {

  val anyClientParam: Gen[Random with Sized, Request] = HttpGen.clientRequest(
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

  def clientParamWithFiniteData(size: Int): Gen[Random with Sized, Request] = HttpGen.clientRequest(
    for {
      content <- Gen.alphaNumericStringBounded(size, size)
      data    <- Gen.fromIterable(List(HttpData.fromString(content)))
    } yield data,
  )

  def spec = suite("EncodeClientParams") {
    testM("method") {
      checkM(anyClientParam) { params =>
        val method = encodeClientParams(HttpVersion.HTTP_1_1, params).map(_.method())
        assertM(method)(equalTo(params.method.asHttpMethod))
      }
    } +
      testM("method on HttpData.File") {
        checkM(HttpGen.clientParamsForFileHttpData) { params =>
          val method = encodeClientParams(HttpVersion.HTTP_1_1, params).map(_.method())
          assertM(method)(equalTo(params.method.asHttpMethod))
        }
      } +
      suite("uri") {
        testM("uri") {
          checkM(anyClientParam) { params =>
            val uri = encodeClientParams(HttpVersion.HTTP_1_1, params).map(_.uri())
            assertM(uri)(equalTo(params.url.relative.encode))
          }
        } +
          testM("uri on HttpData.File") {
            checkM(HttpGen.clientParamsForFileHttpData) { params =>
              val uri = encodeClientParams(HttpVersion.HTTP_1_1, params).map(_.uri())
              assertM(uri)(equalTo(params.url.relative.encode))
            }
          }
      } +
      testM("content-length") {
        checkM(clientParamWithFiniteData(5)) { params =>
          val len = encodeClientParams(HttpVersion.HTTP_1_1, params).map(
            _.headers().getInt(HttpHeaderNames.CONTENT_LENGTH).toLong,
          )
          assertM(len)(equalTo(5L))
        }
      } +
      testM("host header") {
        checkM(anyClientParam) { params =>
          val hostHeader = HttpHeaderNames.HOST
          val headers = encodeClientParams(HttpVersion.HTTP_1_1, params).map(h => Option(h.headers().get(hostHeader)))
          assertM(headers)(equalTo(params.url.host))
        }
      } +
      testM("host header when absolute url") {
        checkM(clientParamWithAbsoluteUrl) { params =>
          val hostHeader = HttpHeaderNames.HOST
          for {
            reqHeaders <- encodeClientParams(HttpVersion.HTTP_1_1, params).map(_.headers())
          } yield assert(reqHeaders.getAll(hostHeader).size)(equalTo(1)) && assert(Option(reqHeaders.get(hostHeader)))(
            equalTo(params.url.host),
          )
        }
      }
  }
}
