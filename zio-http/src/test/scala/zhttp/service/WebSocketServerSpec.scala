package zhttp.service

import zhttp.http.Status
import zhttp.internal.{DynamicServer, HttpRunnableSpec}
import zhttp.service.server._
import zhttp.socket.{Socket, WebSocketFrame}
import zio.stream.ZStream
import zio.test.Assertion.equalTo
import zio.test.TestAspect.timeout
import zio.test._
import zio.{Chunk, ZIO, _}

object WebSocketServerSpec extends HttpRunnableSpec {

  private val env =
    EventLoopGroup.nio() ++ ServerChannelFactory.nio ++ DynamicServer.live ++ ChannelFactory.nio ++ Scope.default
  private val app = serve { DynamicServer.app }

  override def spec = suite("Server") {
    app.as(List(websocketServerSpec, websocketFrameSpec))
  }.provideLayerShared(env) @@ timeout(10 seconds)

  def websocketServerSpec = suite("WebSocketServer") {
    suite("connections") {
      test("Multiple websocket upgrades") {
        val app   = Socket.succeed(WebSocketFrame.text("BAR")).toHttp.deployWS
        val codes = ZIO
          .foreach(1 to 1024)(_ => app(Socket.empty.toSocketApp).map(_.status))
          .map(_.count(_ == Status.SwitchingProtocols))

        assertM(codes)(equalTo(1024))
      }
    }
  }

  def websocketFrameSpec = suite("WebSocketFrameSpec") {
    test("binary") {
      val socket = Socket.collect[WebSocketFrame] { case WebSocketFrame.Binary(buffer) =>
        ZStream.succeed(WebSocketFrame.Binary(buffer))
      }

      val app  = socket.toHttp.deployWS
      val open = Socket.succeed(WebSocketFrame.binary(Chunk.fromArray("Hello, World".getBytes)))

      assertM(app(socket.toSocketApp.onOpen(open)).map(_.status))(equalTo(Status.SwitchingProtocols))
    }
  }
}
