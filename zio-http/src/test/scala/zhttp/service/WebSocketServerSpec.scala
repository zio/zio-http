package zhttp.service

import zhttp.http.{Http, Status}
import zhttp.internal.DynamicServer
import zhttp.service.server._
import zhttp.socket.{Socket, WebSocketFrame}
import zio.clock.Clock
import zio.duration._
import zio.http.internal.HttpRunnableSpec
import zio.stream.ZStream
import zio.test.Assertion.equalTo
import zio.test.TestAspect.{nonFlaky, timeout}
import zio.test._
import zio.test.environment.TestClock
import zio.{Chunk, Promise, ZIO}

object WebSocketServerSpec extends HttpRunnableSpec {

  private val env =
    EventLoopGroup.nio() ++ ServerChannelFactory.nio ++ DynamicServer.live ++ ChannelFactory.nio
  private val app = serve { DynamicServer.app }

  override def spec       = suiteM("Server") {
    app.as(List(websocketServerSpec, websocketFrameSpec, websocketOnCloseSpec)).useNow
  }.provideCustomLayerShared(env) @@ timeout(30 seconds)
  def websocketServerSpec = suite("WebSocketServer") {
    suite("connections") {
      testM("Multiple websocket upgrades") {
        val app   = Socket.succeed(WebSocketFrame.text("BAR")).toHttp.deployWS
        val codes = ZIO
          .foreach(1 to 1024)(_ => app(Socket.empty.toSocketApp).map(_.status))
          .map(_.count(_ == Status.SwitchingProtocols))

        assertM(codes)(equalTo(1024))
      }
    }
  }

  def websocketFrameSpec = suite("WebSocketFrameSpec") {
    testM("binary") {
      val socket = Socket.collect[WebSocketFrame] { case WebSocketFrame.Binary(buffer) =>
        ZStream.succeed(WebSocketFrame.Binary(buffer))
      }

      val app  = socket.toHttp.deployWS
      val open = Socket.succeed(WebSocketFrame.binary(Chunk.fromArray("Hello, World".getBytes)))

      assertM(app(socket.toSocketApp.onOpen(open)).map(_.status))(equalTo(Status.SwitchingProtocols))
    }
  }

  def websocketOnCloseSpec = suite("WebSocketOnCloseSpec") {
    testM("success") {
      for {
        clockEnv <- ZIO.environment[Clock]

        // Maintain a flag to check if the close handler was completed
        isSet     <- Promise.make[Nothing, Unit]
        isStarted <- Promise.make[Nothing, Unit]

        // Setup websocket server
        onClose      = isStarted.succeed(()) <&> isSet.succeed(()).delay(5 seconds)
        serverSocket = Socket.empty.toSocketApp.onClose(_ => onClose)
        serverHttp   = Http.fromZIO(serverSocket.toResponse).deployWS

        // Setup Client
        closeSocket  = Socket.end.delay(1 second)
        clientSocket = Socket.empty.toSocketApp.onOpen(closeSocket)

        // Deploy the server and send it a socket request
        _ <- serverHttp(clientSocket.provideEnvironment(clockEnv))

        // Wait for the close handler to complete

        _ <- TestClock.adjust(2 seconds)
        _ <- isStarted.await
        _ <- TestClock.adjust(5 seconds)
        _ <- isSet.await

        // Check if the close handler was completed
      } yield assertCompletes
    } @@ nonFlaky
  }
}
