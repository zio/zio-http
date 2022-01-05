package zhttp.service

import sttp.client3.asynchttpclient.zio.AsyncHttpClientZioBackend
import zhttp.http._
import zhttp.internal.{AppCollection, HttpRunnableSpec}
import zhttp.service.server._
import zhttp.socket.{Socket, SocketApp, WebSocketFrame}
import zio._
import zio.duration._
import zio.stream.ZStream
import zio.test.Assertion.equalTo
import zio.test.TestAspect.timeout
import zio.test._
import zio.test.environment.TestClock
import zio.test.environment.TestClock.save.delay

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
      testM("should not close connection on errors") {
        val open = Socket.fromStream(ZStream.fail(new Error))
        for {
          r <- Ref.make(true)
          socketApp = SocketApp().onOpen(open).onClose(_ => r.set(false))
          _   <- Http.fromEffect(ZIO(Response.socket(socketApp))).webSocketStatusCode(!! / "subscriptions")
          f   <- delay(10 seconds).fork
          _   <- TestClock.adjust(10 seconds)
          _   <- f.join
          res <- r.get
        } yield assertTrue(res)
      }
    }
  }

  private val env =
    EventLoopGroup.nio() ++ ServerChannelFactory.nio ++ AsyncHttpClientZioBackend
      .layer()
      .orDie ++ AppCollection.live ++ ChannelFactory.nio

  private val app = serve { AppCollection.app }
}
