package zhttp.http

import io.netty.handler.codec.http.{HttpHeaderNames, HttpVersion}
import zhttp.internal.HttpGen
import zhttp.service.{Client, EncodeClientParams}
import zio.random.Random
import zio.test.Assertion._
import zio.test._

import java.net.URI

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
      checkM(anyClientParam) { params =>
        val req = encodeClientParams(HttpVersion.HTTP_1_1, params).map(_.method())
        assertM(req)(equalTo(params.method.asHttpMethod))
      }
    } +
      testM("method on HttpData.File") {
        checkM(HttpGen.clientParamsForFileHttpData()) { params =>
          val req = encodeClientParams(HttpVersion.HTTP_1_1, params).map(_.method())
          assertM(req)(equalTo(params.method.asHttpMethod))
        }
      } +
      suite("uri") {
        testM("uri") {
          checkM(anyClientParam) { params =>
            val req = encodeClientParams(HttpVersion.HTTP_1_1, params).map(_.uri())
            assertM(req)(equalTo(params.url))
          }
        } +
          testM("uri on HttpData.File") {
            checkM(HttpGen.clientParamsForFileHttpData()) { params =>
              val req = encodeClientParams(HttpVersion.HTTP_1_1, params).map(_.uri())
              assertM(req)(equalTo(params.url))
            }
          }
      } +
      testM("content-length") {
        checkM(clientParamWithFiniteData(5)) { params =>
          val req = encodeClientParams(HttpVersion.HTTP_1_1, params).map(
            _.headers().getInt(HttpHeaderNames.CONTENT_LENGTH).toLong,
          )
          assertM(req)(equalTo(5L))
        }
      } +
      testM("host header") {
        checkM(anyClientParam) { params =>
          val req =
            encodeClientParams(HttpVersion.HTTP_1_1, params).map(i => Option(i.headers().get(HttpHeaderNames.HOST)))

          val host = Option(new URI(params.url).getHost)
          assertM(req)(equalTo(host))
        }
      } +
      testM("host header when absolute url") {
        checkM(clientParamWithAbsoluteUrl) { params =>
          for {
            req <- encodeClientParams(HttpVersion.HTTP_1_1, params)
            reqHeaders = req.headers()
            hostHeader = HttpHeaderNames.HOST
            host       = Option(new URI(params.url).getHost)
          } yield assert(reqHeaders.getAll(hostHeader).size)(equalTo(1)) &&
            assert(Option(reqHeaders.get(hostHeader)))(equalTo(host))
        }
      }
  }
}
