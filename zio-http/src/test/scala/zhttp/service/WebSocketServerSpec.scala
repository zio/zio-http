package zhttp.service

import zhttp.http.Status
import zhttp.internal.{DynamicServer, HttpRunnableSpec}
import zhttp.service.server._
import zhttp.socket.{Socket, WebSocketFrame}
import zio._
import zio.test.Assertion.equalTo
import zio.test.TestAspect.timeout
import zio.test._

object WebSocketServerSpec extends HttpRunnableSpec {

  private val env =
    EventLoopGroup.nio() ++ ServerChannelFactory.nio ++ DynamicServer.live ++ ChannelFactory.nio
  private val app = serve { DynamicServer.app }

  override def spec = suite("Server") {
    app.as(List(websocketSpec)).useNow
  }.provideCustomLayerShared(env) @@ timeout(10 seconds)

  def websocketSpec = suite("WebSocket Server") {
    suite("connections") {
      test("Multiple websocket upgrades") {
        val app   = Socket.succeed(WebSocketFrame.text("BAR")).toHttp.deployWS
        val codes = ZIO
          .foreach(1 to 1024)(_ => app(Socket.empty.toSocketApp).map(_.status))
          .map(_.count(_ == Status.SWITCHING_PROTOCOLS))

        assertM(codes)(equalTo(1024))
      }
    }
  }
}
