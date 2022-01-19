package zhttp.service

import zhttp.http._
import zhttp.internal.{DynamicServer, HttpRunnableSpec}
import zhttp.service.server._
import zhttp.socket.{Socket, SocketApp, WebSocketFrame}
import zio.ZIO
import zio.duration._
import zio.stream.ZStream
import zio.test.Assertion.equalTo
import zio.test.TestAspect.timeout
import zio.test.assertM

object WebSocketServerSpec extends HttpRunnableSpec {

  override def spec = suiteM("Server") {
    app.as(List(websocketSpec)).useNow
  }.provideCustomLayerShared(env) @@ timeout(30 seconds)

  def websocketSpec = suite("WebSocket Server") {
    suite("connections") {
      testM("Multiple websocket upgrades") {
        val app = Http.fromZIO {
          Socket
            .collect[WebSocketFrame] { case frame => ZStream.succeed(frame) }
            .toResponse
        }

        val client: SocketApp[Any] = Socket
          .collect[WebSocketFrame] { case WebSocketFrame.Text(_) =>
            ZStream.succeed(WebSocketFrame.close(1000))
          }
          .toSocketApp
          .onOpen(Socket.succeed(WebSocketFrame.Text("FOO")))
          .onClose(_ => ZIO.unit)
          .onError(thr => ZIO.die(thr))

        assertM(app.webSocketStatusCode(!! / "subscriptions", ss = client).repeatN(1024))(
          equalTo(Status.SWITCHING_PROTOCOLS),
        )
      }
    }
  }

  private val env =
    EventLoopGroup.nio() ++ ServerChannelFactory.nio ++ DynamicServer.live ++ ChannelFactory.nio

  private val app = serve { DynamicServer.app }
}
