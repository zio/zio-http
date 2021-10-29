package zhttp.http

import io.netty.handler.codec.http.LastHttpContent
import zhttp.experiment.internal.{HttpGen, HttpMessageAssertions}
import zhttp.service.EventLoopGroup
import zio.duration._
import zio.test.Assertion.{anything, equalTo, isSubtype}
import zio.test.TestAspect.timeout
import zio.test.{DefaultRunnableSpec, Gen, TestFailure, assertM, checkAllM}
import zio.{UIO, ZIO}

object HttpAppResponseSpec extends DefaultRunnableSpec with HttpMessageAssertions {
  private val env = EventLoopGroup.auto(1)

  private val nonEmptyContent = for {
    data    <- Gen.listOf(Gen.alphaNumericString)
    content <- HttpGen.nonEmptyHttpData(Gen.const(data))
    header  <- HttpGen.header
    status  <- HttpGen.status
  } yield (data, content, status, header)

  private val everything = for {
    data    <- Gen.listOf(Gen.alphaNumericString)
    content <- HttpGen.httpData(Gen.const(data))
    header  <- HttpGen.header
    status  <- HttpGen.status
  } yield (data, content, status, header)

  def spec =
    suite("HttpAppResponse") {

      testM("collect") {
        checkAllM(everything) { case (data, content, status, header) =>
          val app = HttpApp.collect { case _ => Response(status, List(header), content) }
          assertM(app.getResponse(content = data))(isResponse {
            responseStatus(status.asJava.code()) &&
            responseHeader(header) &&
            version("HTTP/1.1")
          })
        }
      } +
        testM("collectM") {
          checkAllM(everything) { case (data, content, status, header) =>
            val app = HttpApp.collectM { case _ => UIO(Response(status, List(header), content)) }
            assertM(app.getResponse(content = data))(isResponse {
              responseStatus(status.asJava.code()) &&
              responseHeader(header) &&
              version("HTTP/1.1")
            })
          }
        } +
        testM("fromEffectFunction") {
          checkAllM(everything) { case (data, content, status, header) =>
            val app = HttpApp.fromEffectFunction(_ => UIO(Response(status, List(header), content)))
            assertM(app.getResponse(content = data))(isResponse {
              responseStatus(status.asJava.code()) &&
              responseHeader(header) &&
              version("HTTP/1.1")
            })
          }
        } +
        testM("fromEffectFunction") {
          val app = HttpApp.fromOptionFunction(_ => ZIO.fail(None))
          assertM(app.getResponse())(isResponse {
            responseStatus(404) && version("HTTP/1.1")
          })
        } +
        testM("responseM") {
          checkAllM(everything) { case (data, content, status, header) =>
            val app = HttpApp.responseM(UIO(Response(status, List(header), content)))
            assertM(app.getResponse(content = data))(isResponse {
              responseStatus(status.asJava.code()) &&
              responseHeader(header) &&
              version("HTTP/1.1")
            })
          }
        } +
        testM("text") {
          checkAllM(everything) { case (data, _, _, _) =>
            val app = HttpApp.text(data.mkString(""))
            assertM(app.getContent)(equalTo(data.mkString("")))
          }
        } +
        testM("response") {
          checkAllM(everything) { case (data, content, status, header) =>
            val app = HttpApp.response(Response(status, List(header), content))
            assertM(app.getResponse(content = data))(isResponse {
              responseStatus(status.asJava.code()) &&
              responseHeader(header) &&
              version("HTTP/1.1")
            })
          }
        } +
        testM("notFound") {
          val app = HttpApp.notFound
          assertM(app.getResponse)(isResponse {
            responseStatus(404) && version("HTTP/1.1")
          })
        } +
        testM("Forbidden") {
          val app = HttpApp.forbidden("Permission Denied")
          assertM(app.getResponse)(isResponse {
            responseStatus(403) && version("HTTP/1.1")
          })
        } +
        testM("fromFunction") {
          checkAllM(everything) { case (data, content, status, header) =>
            val app =
              HttpApp.fromFunction(_ => HttpApp.response(Response(status, List(header), content)))
            assertM(app.getResponse(content = data))(isResponse {
              responseStatus(status.asJava.code()) &&
              responseHeader(header) &&
              version("HTTP/1.1")
            })
          }
        } +
        testM("content") {
          checkAllM(nonEmptyContent) { case (data, content, status, header) =>
            val app = HttpApp.fromHttp(Http.succeed(Response(status, List(header), content)))
            assertM(app.getContent(content = data))(equalTo(data.mkString("")))
          }
        } +
        testM("failing Http") {
          val app = HttpApp.fromHttp(Http.fail(new Error("SERVER ERROR")))
          assertM(app.getResponse())(isResponse {
            responseStatus(500) && version("HTTP/1.1")
          })
        } +
        testM("server-error") {
          val app = HttpApp.fromHttp(Http.collectM { case _ => ZIO.fail(new Error("SERVER ERROR")) })
          assertM(app.getResponse())(isResponse {
            responseStatus(500) && version("HTTP/1.1")
          })
        } +
        testM("response is LastHttpContent") {
          val app = HttpApp.fromHttp(Http.collectM { case _ => ZIO.fail(new Error("SERVER ERROR")) })
          assertM(app.getResponse)(isSubtype[LastHttpContent](anything))
        } +
        testM("empty Http") {
          val app = HttpApp.fromHttp(Http.empty)
          assertM(app.getResponse())(isResponse {
            responseStatus(404) && version("HTTP/1.1") && noHeader
          })
        } +
        testM("Http.empty") {
          val app = HttpApp.fromHttp(Http.empty)
          assertM(app.getResponse)(isSubtype[LastHttpContent](anything))
        }
    }.provideCustomLayer(env).mapError(TestFailure.fail) @@ timeout(10 seconds)
}
