package zhttp.service

// import sttp.client3.asynchttpclient.zio.AsyncHttpClientZioBackend
import zhttp.internal.{DynamicServer, HttpRunnableSpec}
import zhttp.service.server._
import zio._
import zio.test.TestAspect.timeout

object WebSocketServerSpec extends HttpRunnableSpec {

  override def spec = suite("Server") {
    serverApp.as(List(websocketSpec)).useNow
  }.provideCustomLayerShared(serverEnv) @@ timeout(30.seconds)

  def websocketSpec = suite("WebSocket Server")() /* {
    suite("connections") {
      testM("Multiple websocket upgrades") {
        val response = Socket.succeed(WebSocketFrame.text("BAR")).toResponse
        val app      = Http.fromEffect(response)
        assertM(app.webSocketStatusCode(!! / "subscriptions").repeatN(1024))(equalTo(101))
      }
    }
  }*/

  def serverEnv =
    EventLoopGroup.nio() ++ ServerChannelFactory.nio ++ /*AsyncHttpClientZioBackend
      .layer()
      .orDie ++*/ DynamicServer.live ++ ChannelFactory.nio

  def serverApp = serve { DynamicServer.app }
}
