package zhttp.socket

import io.netty.handler.codec.http.HttpHeaderNames
import zhttp.experiment.internal.WebSocketMessageAssertions
import zhttp.http.{Header, Http, HttpApp, Request, SocketResponse}
import zhttp.service.EventLoopGroup
import zio.duration.durationInt
import zio.test.TestAspect.timeout
import zio.test.{DefaultRunnableSpec, assertM}

object SocketAppSpec extends DefaultRunnableSpec with WebSocketMessageAssertions {
  private val env       = EventLoopGroup.auto(1)
  // Message Handlers
  private val open      = Socket.succeed(WebSocketFrame.text("Greetings!"))
  private val socketApp = SocketApp.open(open)

  def spec =
    suite("HttpHttpApp")(
      WebSocketUpgradeSpec,
      WebSocketFrameSpec,
    ).provideCustomLayer(env) @@ timeout(10 seconds)

  val wHeaders = List(
    Header.custom(HttpHeaderNames.UPGRADE.toString(), "websocket"),
    Header.custom(HttpHeaderNames.CONNECTION.toString(), "upgrade"),
    Header.custom(HttpHeaderNames.SEC_WEBSOCKET_KEY.toString(), "key"),
  )
  val nHeaders = List(
    Header.custom(HttpHeaderNames.UPGRADE.toString(), "websocket"),
    Header.custom(HttpHeaderNames.CONNECTION.toString(), "upgrade"),
  )

  /**
   * Spec for checking websocket upgrade
   */
  def WebSocketUpgradeSpec = {
    suite("upgrade request")(
      testM("status 101") {
        val res = HttpApp
          .fromHttp(Http.collect[Request] { case req =>
            SocketResponse.from(headers = wHeaders, socketApp = socketApp, req = req)
          })
          .getResponse(header = Header.disassemble(wHeaders))
        assertM(res)(isResponse(responseStatus(101)))
      },
      testM("status 400") {
        val res = HttpApp
          .fromHttp(Http.collect[Request] { case req =>
            SocketResponse.from(headers = nHeaders, socketApp = socketApp, req = req)
          })
          .getResponse(header = Header.disassemble(nHeaders))
        assertM(res)(isResponse(responseStatus(400)))
      },
    )
  }

  /**
   * Spec for checking websocket frame
   */
  def WebSocketFrameSpec = {
    suite("websocket frame")(
      testM("should receive websocket frame") {
        val res = HttpApp
          .fromHttp(Http.collect[Request] { case req =>
            SocketResponse.from(headers = wHeaders, socketApp = socketApp, req = req)
          })
          .getWebSocketFrame(header = Header.disassemble(wHeaders))
        assertM(res)(isResponse(responseStatus(101)))
      },
    )
  }
}
