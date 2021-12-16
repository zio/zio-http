package zhttp.service

import sttp.client3.asynchttpclient.zio.AsyncHttpClientZioBackend
import zhttp.http._
import zhttp.internal.HttpRunnableSpec
import zhttp.service.server._
import zhttp.socket.{Socket, SocketApp, SocketProtocol, WebSocketFrame}
import zio._
import zio.duration._
import zio.stream.ZStream
import zio.test.Assertion.equalTo
import zio.test.TestAspect.timeout
import zio.test._

object WebsocketServerSpec extends HttpRunnableSpec(8011) {
  def spec = suiteM("Server") {
    app.as(List(websocketSpec)).useNow
  }.provideCustomLayer(env) @@ timeout(30 seconds)

  def websocketSpec = suite("Websocket Server") {
    testM("Multiple websocket upgrades") {
      assertM(websocketStatus(!! / "subscription").repeatN(1024))(equalTo(Status.SWITCHING_PROTOCOLS))
    }
  }

  private val env =
    EventLoopGroup.nio() ++ ServerChannelFactory.nio ++ AsyncHttpClientZioBackend.layer().orDie

  private val socket    =
    Socket.collect[WebSocketFrame] { case WebSocketFrame.Text("FOO") =>
      ZStream.succeed(WebSocketFrame.text("BAR"))
    }
  private val socketApp = SocketApp.message(socket) ++ SocketApp.protocol(SocketProtocol.handshakeTimeout(100 millis))
  private val websocketApp = Http.fromEffect(ZIO(Response.socket(socketApp)))

  private val app = serve { websocketApp }
}
