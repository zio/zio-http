package zhttp.experiment

import io.netty.handler.codec.http._
import zhttp.experiment.internal.HttpMessageAssertion
import zhttp.experiment.HttpMessage.HResponse
import zhttp.http.{Header, Http}
import zhttp.service.EventLoopGroup
import zio.duration._
import zio.test.DefaultRunnableSpec
import zio.test.TestAspect.timeout

/**
 * Be prepared for some real nasty runtime tests.
 */
object HAppSpec extends DefaultRunnableSpec with HttpMessageAssertion {
  private val env = EventLoopGroup.auto(1)

  type DReq = DefaultHttpRequest

  def spec = suite("HApp")(
    suite("empty")(
      testM("status is 404") {
        HApp.empty === isResponse(status(404))
      },
      testM("headers are empty") {
        HApp.empty === isResponse(noHeader)
      },
      testM("version is 1.1") {
        HApp.empty === isResponse(version("HTTP/1.1"))
      },
      testM("version is 1.1") {
        HApp.empty === isResponse(version("HTTP/1.1"))
      },
    ),
    suite("succeed(empty)")(
      testM("status is 404") {
        HApp.empty === isResponse(status(404))
      },
      testM("headers are empty") {
        HApp.empty === isResponse(noHeader)
      },
      testM("version is 1.1") {
        HApp.empty === isResponse(version("HTTP/1.1"))
      },
      testM("version is 1.1") {
        HApp.empty === isResponse(version("HTTP/1.1"))
      },
    ),
    suite("succeed(ok)")(
      testM("status is 200") {
        HApp.succeed(Http.succeed(HResponse())) === isResponse(status(200))
      },
      testM("headers are empty") {
        HApp.succeed(Http.succeed(HResponse())) === isResponse(noHeader)
      },
      testM("headers are set") {
        HApp.succeed(Http.succeed(HResponse(headers = List(Header("key", "value"))))) === isResponse(
          header("key", "value"),
        )
      },
      testM("version is 1.1") {
        HApp.succeed(Http.succeed(HResponse())) === isResponse(version("HTTP/1.1"))
      },
      testM("version is 1.1") {
        HApp.succeed(Http.succeed(HResponse())) === isResponse(version("HTTP/1.1"))
      },
    ),
    suite("succeed(fail)")(
      testM("status is 500") {
        HApp.succeed(Http.fail(new Error("SERVER_ERROR"))) === isResponse(status(500))
      },
      testM("content is SERVER_ERROR") {
        HApp.succeed(Http.fail(new Error("SERVER_ERROR"))) === isResponse(isContent(bodyText("SERVER_ERROR")))
      },
      testM("headers are set") {
        HApp.succeed(Http.fail(new Error("SERVER_ERROR"))) === isResponse(header("content-length"))
      },
    ),
  ).provideCustomLayer(env) @@ timeout(5 second)
}
