package zhttp.service

import zhttp.http.{Headers, Http, Status}
import zhttp.internal.{DynamicServer, HttpRunnableSpec}
import zhttp.service.ChannelEvent.UserEvent.HandshakeComplete
import zhttp.service.ChannelEvent.{ChannelRead, ChannelUnregistered, UserEventTriggered}
import zhttp.service.server._
import zhttp.socket.{WebSocketChannelEvent, WebSocketFrame}
import zio.clock.Clock
import zio.duration._
import zio.test.Assertion.equalTo
import zio.test.TestAspect.{nonFlaky, timeout}
import zio.test._
import zio.test.environment.TestClock
import zio.{Promise, Ref, UIO, ZIO}

object WebSocketServerSpec extends HttpRunnableSpec {

  private val env           =
    EventLoopGroup.nio() ++ ServerChannelFactory.nio ++ DynamicServer.live ++ ChannelFactory.nio
  private val websocketSpec = suite("WebsocketSpec")(
    testM("channel events between client and server") {
      for {
        msg <- MessageCollector.make[ChannelEvent.Event[WebSocketFrame]]
        url <- DynamicServer.wsURL
        id  <- DynamicServer.deploy {
          Http
            .collectZIO[WebSocketChannelEvent] {
              case ChannelEvent(ch, ev @ ChannelRead(frame)) => ch.writeAndFlush(frame) *> msg.add(ev)
              case ChannelEvent(_, ev @ ChannelUnregistered) => msg.add(ev, true)
              case ChannelEvent(_, ev)                       => msg.add(ev)
            }
            .toSocketApp
            .toHttp
        }

        res <- Http
          .collectZIO[WebSocketChannelEvent] {
            case ChannelEvent(ch, UserEventTriggered(HandshakeComplete))   =>
              ch.writeAndFlush(WebSocketFrame.text("FOO"))
            case ChannelEvent(ch, ChannelRead(WebSocketFrame.Text("FOO"))) =>
              ch.writeAndFlush(WebSocketFrame.text("BAR"))
            case ChannelEvent(ch, ChannelRead(WebSocketFrame.Text("BAR"))) =>
              ch.close()
          }
          .toSocketApp
          .connect(url, Headers(DynamicServer.APP_ID, id))
          .use { _ =>
            for {
              events <- msg.await
              expected = List(
                UserEventTriggered(HandshakeComplete),
                ChannelRead(WebSocketFrame.text("FOO")),
                ChannelRead(WebSocketFrame.text("BAR")),
                ChannelUnregistered,
              )
            } yield assertTrue(events == expected)
          }
      } yield res
    },
    testM("on close interruptibility") {
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
        // Client closes the connection after 1 second
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
    } @@ nonFlaky,
    testM("Multiple websocket upgrades") {
      val app   = Http.succeed(WebSocketFrame.text("BAR")).toSocketApp.toHttp.deployWS
      val codes = ZIO
        .foreach(1 to 1024)(_ => app(Http.empty.toSocketApp).map(_.status))
        .map(_.count(_ == Status.SwitchingProtocols))

      assertM(codes)(equalTo(1024))
    },
  )

  override def spec = suiteM("Server") {
    serve {
      DynamicServer.app
    }.as(List(websocketSpec)).useNow
  }
    .provideCustomLayerShared(env) @@ timeout(30 seconds)

  final class MessageCollector[A](ref: Ref[List[A]], promise: Promise[Nothing, Unit]) {
    def add(a: A, isDone: Boolean = false): UIO[Unit] = ref.update(_ :+ a) *> promise.succeed(()).when(isDone)

    def await: UIO[List[A]] = promise.await *> ref.get

    def done: UIO[Boolean] = promise.succeed(())
  }

  object MessageCollector {
    def make[A]: ZIO[Any, Nothing, MessageCollector[A]] = for {
      ref <- Ref.make(List.empty[A])
      prm <- Promise.make[Nothing, Unit]
    } yield new MessageCollector(ref, prm)
  }
}
