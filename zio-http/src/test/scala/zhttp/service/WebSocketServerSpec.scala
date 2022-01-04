package zhttp.service

import sttp.client3.asynchttpclient.zio.AsyncHttpClientZioBackend
import zhttp.http._
import zhttp.internal.{AppCollection, HttpRunnableSpec}
import zhttp.service.server._
import zhttp.socket.{Socket, SocketApp, WebSocketFrame}
import zio._
import zio.duration._
import zio.test.Assertion.equalTo
import zio.test.TestAspect.timeout
import zio.test._

object WebSocketServerSpec extends HttpRunnableSpec(8011) {

  override def spec = suiteM("Server") {
    app.as(List(websocketSpec)).useNow
  }.provideCustomLayerShared(env) @@ timeout(30 seconds)

  def websocketSpec = suite("WebSocket Server") {
    suite("connections") {
      testM("Multiple websocket upgrades") {
        val socketApp = SocketApp(Socket.succeed(WebSocketFrame.text("BAR")))
        val app       = Http.fromEffect(ZIO(Response.socket(socketApp)))
        assertM(app.webSocketStatusCode(!! / "subscriptions").repeatN(1024))(equalTo(101))
      }
    }
  }

  private val env =
    EventLoopGroup.nio() ++ ServerChannelFactory.nio ++ AsyncHttpClientZioBackend
      .layer()
      .orDie ++ AppCollection.live ++ ChannelFactory.nio

  private val app = serve { AppCollection.app }
}
