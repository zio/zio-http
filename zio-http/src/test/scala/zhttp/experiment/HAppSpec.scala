package zhttp.experiment

import io.netty.buffer.ByteBuf
import io.netty.handler.codec.http._
import zhttp.experiment.HttpMessage.HResponse
import zhttp.experiment.internal.{ChannelProxy, HttpMessageAssertion}
import zhttp.http.{HTTP_CHARSET, Header, Http}
import zhttp.service.EventLoopGroup
import zio.Promise
import zio.duration._
import zio.test.Assertion.equalTo
import zio.test.TestAspect.timeout
import zio.test._

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
        HApp.from(Http.succeed(HResponse())) === isResponse(status(200))
      },
      testM("headers are empty") {
        HApp.from(Http.succeed(HResponse())) === isResponse(noHeader)
      },
      testM("headers are set") {
        HApp.from(Http.succeed(HResponse(headers = List(Header("key", "value"))))) === isResponse(
          header("key", "value"),
        )
      },
      testM("version is 1.1") {
        HApp.from(Http.succeed(HResponse())) === isResponse(version("HTTP/1.1"))
      },
      testM("version is 1.1") {
        HApp.from(Http.succeed(HResponse())) === isResponse(version("HTTP/1.1"))
      },
    ),
    suite("succeed(fail)")(
      testM("status is 500") {
        HApp.from(Http.fail(new Error("SERVER_ERROR"))) === isResponse(status(500))
      },
      testM("content is SERVER_ERROR") {
        HApp.from(Http.fail(new Error("SERVER_ERROR"))) === isResponse(isContent(bodyText("SERVER_ERROR")))
      },
      testM("headers are set") {
        HApp.from(Http.fail(new Error("SERVER_ERROR"))) === isResponse(header("content-length"))
      },
    ),
    suite("fail(cause)")(
      testM("status is 500") {
        HApp.fail(new Error("SERVER_ERROR")) === isResponse(status(500))
      },
      testM("content is SERVER_ERROR") {
        HApp.fail(new Error("SERVER_ERROR")) === isResponse(isContent(bodyText("SERVER_ERROR")))
      },
      testM("headers are set") {
        HApp.fail(new Error("SERVER_ERROR")) === isResponse(header("content-length"))
      },
    ),
    suite("succeed(CompleteRequest)")(
      testM("status is 200") {
        for {
          proxy <- ChannelProxy.make(HApp.from(Http.collect[CompleteRequest[ByteBuf]](req => HResponse())))
          _     <- proxy.request()
          _     <- proxy.last
          res   <- proxy.receive
        } yield assert(res)(isResponse(status(200)))
      },
      testM("req.content is Hello World") {
        for {
          promise <- Promise.make[Nothing, ByteBuf]
          proxy   <- ChannelProxy.make(
            HApp.from(Http.collectM[CompleteRequest[ByteBuf]](req => promise.succeed(req.content) as HResponse())),
          )
          _       <- proxy.post()
          _       <- proxy.content("Hello")
          _       <- proxy.content("World", true)
          res     <- promise.await.map(bytes => bytes.toString(HTTP_CHARSET))
        } yield assert(res)(equalTo("HelloWorld"))
      },
    ),
  ).provideCustomLayer(env) @@ timeout(5 second)

}
