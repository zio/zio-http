package zhttp.http

import io.netty.handler.codec.http.HttpHeaderNames
import zhttp.internal.HttpGen
import zhttp.service.{Client, EncodeClientParams}
import zio.random.Random
import zio.test.Assertion._
import zio.test._

object EncodeClientRequestSpec extends DefaultRunnableSpec with EncodeClientParams {

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
    testM("method") {
      check(anyClientParam) { params =>
        val req = encodeClientParams(params)
        assert(req.method())(equalTo(params.method.asHttpMethod))
      }
    } +
      testM("method on HttpData.File") {
        check(HttpGen.clientParamsForFileHttpData()) { params =>
          val req = encodeClientParams(params)
          assert(req.method())(equalTo(params.method.asHttpMethod))
        }
      } +
      suite("uri") {
        testM("uri") {
          check(anyClientParam) { params =>
            val req = encodeClientParams(params)
            assert(req.uri())(equalTo(params.url.relative.encode))
          }
        } +
          testM("uri on HttpData.File") {
            check(HttpGen.clientParamsForFileHttpData()) { params =>
              val req = encodeClientParams(params)
              assert(req.uri())(equalTo(params.url.relative.encode))
            }
          }
      } +
      testM("content-length") {
        check(clientParamWithFiniteData(5)) { params =>
          val req = encodeClientParams(params)
          assert(req.headers().getInt(HttpHeaderNames.CONTENT_LENGTH).toLong)(equalTo(5L))
        }
      } +
      testM("host header") {
        check(anyClientParam) { params =>
          val req        = encodeClientParams(params)
          val hostHeader = HttpHeaderNames.HOST
          assert(Option(req.headers().get(hostHeader)))(equalTo(params.url.host))
        }
      } +
      testM("host header when absolute url") {
        check(clientParamWithAbsoluteUrl) { params =>
          val req        = encodeClientParams(params)
          val reqHeaders = req.headers()
          val hostHeader = HttpHeaderNames.HOST

          assert(reqHeaders.getAll(hostHeader).size)(equalTo(1)) &&
          assert(Option(reqHeaders.get(hostHeader)))(equalTo(params.url.host))
        }
      }
  }
}
