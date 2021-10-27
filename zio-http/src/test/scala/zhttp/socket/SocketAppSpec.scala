package zhttp.socket

import zhttp.experiment.internal.WebSocketMessageAssertions
import zhttp.http.{Http, HttpApp, Request, SocketResponse}
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
      WebSocketSpec,
    ).provideCustomLayer(env) @@ timeout(10 seconds)

  /**
   * Spec for checking websocket upgrade
   */
  def WebSocketSpec = {
    suite("upgrade request")(
      testM("status is 101") {
        val res = HttpApp
          .fromHttp(Http.collect[Request] { case req => SocketResponse.from(socketApp = socketApp, req = req) })
          .getResponse()
        assertM(res)(isResponse(responseStatus(101)))
      },
    )
  }
}
