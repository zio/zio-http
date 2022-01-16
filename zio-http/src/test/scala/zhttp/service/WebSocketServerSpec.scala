package zhttp.service

import sttp.client3.asynchttpclient.zio.AsyncHttpClientZioBackend
import zhttp.http._
import zhttp.internal.{DynamicServer, HttpRunnableSpec}
import zhttp.service.server._
import zhttp.socket.{Socket, WebSocketFrame}
import zio.duration._
import zio.test.Assertion.equalTo
import zio.test.TestAspect.timeout
import zio.test._

object WebSocketServerSpec extends HttpRunnableSpec {

  override def spec = suiteM("Server") {
    app.as(List(websocketSpec)).useNow
  }.provideCustomLayerShared(env) @@ timeout(30 seconds)

  def websocketSpec = suite("WebSocket Server") {
    suite("connections") {
      testM("Multiple websocket upgrades") {
        val response = Socket.succeed(WebSocketFrame.text("BAR")).toResponse
        val app      = Http.fromZIO(response)
        assertM(app.webSocketStatusCode(!! / "subscriptions").repeatN(1024))(equalTo(101))
      }
    }
  }

  private val env =
    EventLoopGroup.nio() ++ ServerChannelFactory.nio ++ AsyncHttpClientZioBackend
      .layer()
      .orDie ++ DynamicServer.live ++ ChannelFactory.nio

  private val app = serve { DynamicServer.app }
}
