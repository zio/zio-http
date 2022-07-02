package zhttp.service

import zhttp.http.{Http, Status}
import zhttp.internal.{DynamicServer, HttpRunnableSpec}
import zhttp.service.ChannelEvent.Event.{ChannelRead, ChannelUnregistered, UserEventTriggered}
import zhttp.service.ChannelEvent.UserEvent.HandshakeComplete
import zhttp.service.server._
import zhttp.socket.{WebSocketChannelEvent, WebSocketFrame}
import zio.clock.Clock
import zio.duration._
import zio.test.Assertion.equalTo
import zio.test.TestAspect.{nonFlaky, timeout}
import zio.test._
import zio.test.environment.TestClock
import zio.{Chunk, Promise, ZIO}

object WebSocketServerSpec extends HttpRunnableSpec {

  private val env =
    EventLoopGroup.nio() ++ ServerChannelFactory.nio ++ DynamicServer.live ++ ChannelFactory.nio
  private val app = serve { DynamicServer.app }

  override def spec = suiteM("Server") {
    app.as(List(websocketServerSpec, websocketFrameSpec, websocketOnCloseSpec)).useNow
  }.provideCustomLayerShared(env) @@ timeout(30 seconds)

  def websocketFrameSpec = suite("WebSocketFrameSpec") {
    testM("binary") {
      val socket = Http.collectZIO[WebSocketChannelEvent] {
        case ChannelEvent(ch, ChannelRead(WebSocketFrame.Binary(buffer))) =>
          ch.writeAndFlush(WebSocketFrame.Binary(buffer))

        case ChannelEvent(ch, UserEventTriggered(HandshakeComplete)) =>
          ch.writeAndFlush(WebSocketFrame.binary(Chunk.fromArray("Hello, World".getBytes)))
      }

      val app = socket.toSocketApp.toHttp.deployWS

      assertM(app(socket.toSocketApp).map(_.status))(equalTo(Status.SwitchingProtocols))
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

        serverHttp   = Http
          .collectZIO[WebSocketChannelEvent] { case ChannelEvent(_, ChannelUnregistered) =>
            isStarted.succeed(()) <&> isSet.succeed(()).delay(5 seconds)
          }
          .toSocketApp
          .toHttp
          .deployWS

        // Setup Client
        // Client closes the connection after 1 secon
        clientSocket = Http
          .collectZIO[WebSocketChannelEvent] { case ChannelEvent(ch, UserEventTriggered(HandshakeComplete)) =>
            ch.writeAndFlush(WebSocketFrame.close(1000)).delay(1 second)
          }
          .toSocketApp

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

  def websocketServerSpec = suite("WebSocketServer") {
    suite("connections") {
      testM("Multiple websocket upgrades") {
        val app   = Http.succeed(WebSocketFrame.text("BAR")).toSocketApp.toHttp.deployWS
        val codes = ZIO
          .foreach(1 to 1024)(_ => app(Http.empty.toSocketApp).map(_.status))
          .map(_.count(_ == Status.SwitchingProtocols))

        assertM(codes)(equalTo(1024))
      }
    }
  }
}
