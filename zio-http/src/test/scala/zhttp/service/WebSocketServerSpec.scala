package zhttp.service

import zhttp.http.Status
import zhttp.internal.{DynamicServer, HttpRunnableSpec}
import zhttp.service.server._
import zhttp.socket.{Socket, WebSocketFrame}
import zio.clock.Clock
import zio.duration._
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
      val app = Socket.empty.toHttp.deployWS

      // Close the connection after 1 second
      val closeSocket = Socket.end.delay(1 second)

      for {
        clock <- ZIO.environment[Clock]

        // Maintain a flag to check if the close handler was completed
        isSet <- Promise.make[Nothing, Unit]

        // Sets the ref after 5 seconds
        onClose = isSet.succeed(()).delay(5 seconds).debug("Test: OnClose")

        // Create a client socket
        clientSocket = Socket.empty.toSocketApp.onOpen(closeSocket).onClose(_ => onClose)

        // Deploy the server and send it a socket request
        _ <- app(clientSocket.provideEnvironment(clock))

        // Wait for the close handler to complete
        _ <- TestClock.adjust(10 seconds)

        // Check if the close handler was completed
        _ <- isSet.await
      } yield assertCompletes
    }
  } @@ nonFlaky
}
