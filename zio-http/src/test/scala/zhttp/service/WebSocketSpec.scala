package zhttp.service

import zhttp.http.{Headers, Http, Status}
import zhttp.internal.{DynamicServer, HttpRunnableSpec, testClient, websocketTestClient}
import zhttp.service.ChannelEvent.UserEvent.HandshakeComplete
import zhttp.service.ChannelEvent.{ChannelRead, ChannelUnregistered, UserEventTriggered}
import zhttp.socket.{WebSocketChannelEvent, WebSocketFrame}
import zio._
import zio.test.Assertion.equalTo
import zio.test.TestAspect._
import zio.test._

object WebSocketSpec extends HttpRunnableSpec {

  private val env =
    EventLoopGroup.nio() ++ DynamicServer.live ++ Scope.default

  private val websocketSpec = suite("WebsocketSpec")(
    test("channel events between client and server") {
      for {
        msg    <- MessageCollector.make[ChannelEvent.Event[WebSocketFrame]]
        url    <- DynamicServer.wsURL
        id     <- DynamicServer.deploy {
          Http
            .collectZIO[WebSocketChannelEvent] {
              case ev @ ChannelEvent(ch, ChannelRead(frame)) => ch.writeAndFlush(frame) *> msg.add(ev.event)
              case ev @ ChannelEvent(_, ChannelUnregistered) => msg.add(ev.event, true)
              case ev @ ChannelEvent(_, _)                   => msg.add(ev.event)
            }
            .toSocketApp
            .toHttp
        }
        client <- testClient
        res    <- ZIO.scoped {
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
            .connect(url, Headers(DynamicServer.APP_ID, id), client) *> {
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
        client <- websocketTestClient
        serverHttp   = Http
          .collectZIO[WebSocketChannelEvent] { case ChannelEvent(_, ChannelUnregistered) =>
            isStarted.succeed(()) <&> isSet.succeed(()).delay(5 seconds).withClock(clock)
          }
          .toSocketApp
          .toHttp
          .deployWS(client)

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
      val app   = Http
        .fromZIO(websocketTestClient)
        .flatMap(client => Http.succeed(WebSocketFrame.text("BAR")).toSocketApp.toHttp.deployWS(client))
      val codes = ZIO
        .foreach(1 to 1024)(_ => app(Http.empty.toSocketApp).map(_.status))
        .map(_.count(_ == Status.SwitchingProtocols))

      assertZIO(codes)(equalTo(1024))
    },
  )

  override def spec = suite("Server") {
    serve {
      DynamicServer.app
    }.as(List(websocketSpec))
  }
    .provideLayerShared(env) @@ timeout(30 seconds)

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
