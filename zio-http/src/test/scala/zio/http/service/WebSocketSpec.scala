package zio.http.service

import zio._
import zio.http.ChannelEvent.UserEvent.HandshakeComplete
import zio.http.ChannelEvent.{ChannelRead, ChannelUnregistered, UserEventTriggered}
import zio.http._
import zio.http.internal.{DynamicServer, HttpRunnableSpec, severTestLayer}
import zio.http.model.{Headers, Status}
import zio.http.socket.{WebSocketChannelEvent, WebSocketFrame}
import zio.test.Assertion.equalTo
import zio.test.TestAspect.{nonFlaky, timeout}
import zio.test.{TestClock, assertCompletes, assertTrue, assertZIO, testClock}

object WebSocketSpec extends HttpRunnableSpec {

  private val websocketSpec = suite("WebsocketSpec")(
    test("channel events between client and server") {
      for {
        msg <- MessageCollector.make[ChannelEvent.Event[WebSocketFrame]]
        url <- DynamicServer.wsURL
        id  <- DynamicServer.deploy {
          Http
            .collectZIO[WebSocketChannelEvent] {
              case ev @ ChannelEvent(ch, ChannelRead(frame)) => ch.writeAndFlush(frame) *> msg.add(ev.event)
              case ev @ ChannelEvent(_, ChannelUnregistered) => msg.add(ev.event, true)
              case ev @ ChannelEvent(_, _)                   => msg.add(ev.event)
            }
            .toSocketApp
            .toHttp
        }

        res <- ZIO.scoped {
          Http
            .collectZIO[WebSocketChannelEvent] {
              case ChannelEvent(ch, UserEventTriggered(HandshakeComplete))   =>
                ch.writeAndFlush(WebSocketFrame.text("FOO"))
              case ChannelEvent(ch, ChannelRead(WebSocketFrame.Text("FOO"))) =>
                ch.writeAndFlush(WebSocketFrame.text("BAR"))
              case ChannelEvent(ch, ChannelRead(WebSocketFrame.Text("BAR"))) =>
                ch.close()
            }
            .toSocketApp
            .connect(url, Headers(DynamicServer.APP_ID, id)) *> {
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
        }
      } yield res
    },
    test("on close interruptibility") {
      for {

        // Maintain a flag to check if the close handler was completed
        isSet     <- Promise.make[Nothing, Unit]
        isStarted <- Promise.make[Nothing, Unit]
        clock     <- testClock

        // Setup websocket server

        serverHttp   = Http
          .collectZIO[WebSocketChannelEvent] { case ChannelEvent(_, ChannelUnregistered) =>
            isStarted.succeed(()) <&> isSet.succeed(()).delay(5 seconds).withClock(clock)
          }
          .toSocketApp
          .toHttp
          .deployWS

        // Setup Client
        // Client closes the connection after 1 second
        clientSocket = Http
          .collectZIO[WebSocketChannelEvent] { case ChannelEvent(ch, UserEventTriggered(HandshakeComplete)) =>
            ch.writeAndFlush(WebSocketFrame.close(1000)).delay(1 second).withClock(clock)
          }
          .toSocketApp

        // Deploy the server and send it a socket request
        _ <- serverHttp(clientSocket)

        // Wait for the close handler to complete
        _ <- TestClock.adjust(2 seconds)
        _ <- isStarted.await
        _ <- TestClock.adjust(5 seconds)
        _ <- isSet.await

        // Check if the close handler was completed
      } yield assertCompletes
    } @@ nonFlaky,
    test("Multiple websocket upgrades") {
      val app   = Http.succeed(WebSocketFrame.text("BAR")).toSocketApp.toHttp.deployWS
      val codes = ZIO
        .foreach(1 to 1024)(_ => app(Http.empty.toSocketApp).map(_.status))
        .map(_.count(_ == Status.SwitchingProtocols))

      assertZIO(codes)(equalTo(1024))
    },
  )

  override def spec = suite("Server") {
    ZIO.scoped {
      serve {
        DynamicServer.app
      }.as(List(websocketSpec))
    }
  }
    .provideShared(DynamicServer.live, severTestLayer, Client.default, Scope.default) @@
    timeout(30 seconds)

  final class MessageCollector[A](ref: Ref[List[A]], promise: Promise[Nothing, Unit]) {
    def add(a: A, isDone: Boolean = false): UIO[Unit] = ref.update(_ :+ a) <* promise.succeed(()).when(isDone)
    def await: UIO[List[A]]                           = promise.await *> ref.get
    def done: UIO[Boolean]                            = promise.succeed(())
  }

  object MessageCollector {
    def make[A]: ZIO[Any, Nothing, MessageCollector[A]] = for {
      ref <- Ref.make(List.empty[A])
      prm <- Promise.make[Nothing, Unit]
    } yield new MessageCollector(ref, prm)
  }
}
