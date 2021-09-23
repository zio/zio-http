package zhttp.experiment
import io.netty.handler.codec.http.LastHttpContent
import zhttp.experiment.internal._
import zhttp.http._
import zhttp.service.EventLoopGroup
import zio._
import zio.duration.durationInt
import zio.test.Assertion.{anything, equalTo, isSubtype}
import zio.test.TestAspect._
import zio.test._

object HttpEndpointResponseSpec extends DefaultRunnableSpec with HttpMessageAssertions {
  private val env = EventLoopGroup.auto(1)

  private val nonEmptyContent = for {
    data    <- Gen.listOf(Gen.alphaNumericString)
    content <- HttpGen.nonEmptyContent(Gen.const(data))
    header  <- HttpGen.header
    status  <- HttpGen.status
  } yield (data, content, status, header)

  private val everything = for {
    data    <- Gen.listOf(Gen.alphaNumericString)
    content <- HttpGen.content(Gen.const(data))
    header  <- HttpGen.header
    status  <- HttpGen.status
  } yield (data, content, status, header)

  def spec =
    suite("HttpEndpointResponse") {

      testM("collect") {
        checkAllM(everything) { case (data, content, status, header) =>
          val endpoint = HttpEndpoint.collect(_ => Response(status, List(header), content))
          assertM(endpoint.getResponse(content = data))(isResponse {
            responseStatus(status.asJava.code()) &&
            responseHeader(header) &&
            version("HTTP/1.1")
          })
        }
      } +
        testM("collectM") {
          checkAllM(everything) { case (data, content, status, header) =>
            val endpoint = HttpEndpoint.collectM(_ => UIO(Response(status, List(header), content)))
            assertM(endpoint.getResponse(content = data))(isResponse {
              responseStatus(status.asJava.code()) &&
              responseHeader(header) &&
              version("HTTP/1.1")
            })
          }
        } +
        testM("fromEffectFunction") {
          checkAllM(everything) { case (data, content, status, header) =>
            val endpoint = HttpEndpoint.fromEffectFunction(_ => UIO(AnyResponse(status, List(header), content)))
            assertM(endpoint.getResponse(content = data))(isResponse {
              responseStatus(status.asJava.code()) &&
              responseHeader(header) &&
              version("HTTP/1.1")
            })
          }
        } +
        testM("responseM") {
          checkAllM(everything) { case (data, content, status, header) =>
            val endpoint = HttpEndpoint.responseM(UIO(AnyResponse(status, List(header), content)))
            assertM(endpoint.getResponse(content = data))(isResponse {
              responseStatus(status.asJava.code()) &&
              responseHeader(header) &&
              version("HTTP/1.1")
            })
          }
        } +
        testM("text") {
          checkAllM(everything) { case (data, _, _, _) =>
            val endpoint = HttpEndpoint.text(data.mkString(""))
            assertM(endpoint.getContent)(equalTo(data.mkString("")))
          }
        } +
        testM("response") {
          checkAllM(everything) { case (data, content, status, header) =>
            val endpoint = HttpEndpoint.response(AnyResponse(status, List(header), content))
            assertM(endpoint.getResponse(content = data))(isResponse {
              responseStatus(status.asJava.code()) &&
              responseHeader(header) &&
              version("HTTP/1.1")
            })
          }
        } +
        testM("notFound") {
          val endpoint = HttpEndpoint.notFound
          assertM(endpoint.getResponse)(isResponse {
            responseStatus(404) && version("HTTP/1.1")
          })
        } +
        testM("Forbidden") {
          val endpoint = HttpEndpoint.forbidden("Permission Denied")
          assertM(endpoint.getResponse)(isResponse {
            responseStatus(403) && version("HTTP/1.1")
          })
        } +
        testM("fromFunction") {
          checkAllM(everything) { case (data, content, status, header) =>
            val endpoint =
              HttpEndpoint.fromFunction(_ => HttpEndpoint.response(AnyResponse(status, List(header), content)))
            assertM(endpoint.getResponse(content = data))(isResponse {
              responseStatus(status.asJava.code()) &&
              responseHeader(header) &&
              version("HTTP/1.1")
            })
          }
        } +
        testM("content") {
          checkAllM(nonEmptyContent) { case (data, content, status, header) =>
            val endpoint = HttpEndpoint.mount(Http.collect(_ => Response(status, List(header), content)))
            assertM(endpoint.getContent(content = data))(equalTo(data.mkString("")))
          }
        } +
        testM("failing Http") {
          val endpoint = HttpEndpoint.mount(Http.fail(new Error("SERVER ERROR")))
          assertM(endpoint.getResponse())(isResponse {
            responseStatus(500) && version("HTTP/1.1")
          })
        } +
        testM("server-error") {
          val endpoint = HttpEndpoint.mount(Http.collectM(_ => ZIO.fail(new Error("SERVER ERROR"))))
          assertM(endpoint.getResponse())(isResponse {
            responseStatus(500) && version("HTTP/1.1")
          })
        } +
        testM("response is LastHttpContent") {
          val endpoint = HttpEndpoint.mount(Http.collectM(_ => ZIO.fail(new Error("SERVER ERROR"))))
          assertM(endpoint.getResponse)(isSubtype[LastHttpContent](anything))
        } +
        testM("empty Http") {
          val endpoint = HttpEndpoint.mount(Http.empty)
          assertM(endpoint.getResponse())(isResponse {
            responseStatus(404) && version("HTTP/1.1") && noHeader
          })
        } +
        testM("Http.empty") {
          val endpoint = HttpEndpoint.mount(Http.empty)
          assertM(endpoint.getResponse)(isSubtype[LastHttpContent](anything))
        }
    }.provideCustomLayer(env) @@ timeout(10 seconds)
}
