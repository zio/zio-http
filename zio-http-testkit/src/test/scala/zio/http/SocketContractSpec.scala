package zio.http

import zio.Console.printLine
import zio._
import zio.http.ChannelEvent.{ChannelRead, ChannelUnregistered, UserEvent, UserEventTriggered}
import zio.http.model.Status
import zio.http.netty.server.NettyDriver
import zio.http.socket._
import zio.test._

object SocketContractSpec extends ZIOSpecDefault {

  private val messageFilter: Http[Any, Nothing, WebSocketChannelEvent, (Channel[WebSocketFrame], String)] =
    Http.collect[WebSocketChannelEvent] { case ChannelEvent(channel, ChannelRead(WebSocketFrame.Text(message))) =>
      (channel, message)
    }

  private val warnOnUnrecognizedEvent = Http.collectZIO[WebSocketChannelEvent] { case other =>
    ZIO.fail(new Exception("Unexpected event: " + other))
  }

  def spec =
    suite("SocketOps")(
      contract("Successful Multi-message application") { p =>
        def channelSocketServer: Http[Any, Throwable, WebSocketChannelEvent, Unit] =
          Http
            .collectZIO[WebSocketChannelEvent] {
              case ChannelEvent(ch, UserEventTriggered(UserEvent.HandshakeComplete)) =>
                ch.writeAndFlush(WebSocketFrame.text("Hi Client"))
              case ChannelEvent(_, ChannelUnregistered)                              =>
                p.succeed(()) *>
                  printLine("Server Channel unregistered")
              case ChannelEvent(ch, ChannelRead(WebSocketFrame.Text("Hi Server")))   =>
                ch.close()
              case ChannelEvent(_, other)                                            =>
                printLine("Server Unexpected: " + other)
            }
            .defaultWith(warnOnUnrecognizedEvent)

        val messageSocketServer: Http[Any, Throwable, WebSocketChannelEvent, Unit] = messageFilter >>>
          Http.collectZIO[(WebSocketChannel, String)] {
            case (ch, text) if text.contains("Hi Server") =>
              printLine("Server got message: " + text) *> ch.close()
          }

        messageSocketServer
          .defaultWith(channelSocketServer)
      } { _ =>
        val messageSocketClient: Http[Any, Throwable, WebSocketChannelEvent, Unit] = messageFilter >>>
          Http.collectZIO[(WebSocketChannel, String)] {
            case (ch, text) if text.contains("Hi Client") =>
              ch.writeAndFlush(WebSocketFrame.text("Hi Server"), await = true)
          }

        val channelSocketClient: Http[Any, Throwable, WebSocketChannelEvent, Unit] =
          Http.collectZIO[WebSocketChannelEvent] {
            case ChannelEvent(_, ChannelUnregistered) =>
              printLine("Client Channel unregistered")

            case ChannelEvent(_, other) =>
              printLine("Client received Unexpected event: " + other)
          }

        messageSocketClient.defaultWith(channelSocketClient)
      },
      contract("Application where server app fails")(_ =>
        Http.collectZIO[WebSocketChannelEvent] {
          case ChannelEvent(_, UserEventTriggered(UserEvent.HandshakeComplete)) =>
            ZIO.fail(new Exception("Broken server"))
        },
      ) { p =>
        Http.collectZIO[WebSocketChannelEvent] { case ChannelEvent(_, ChannelUnregistered) =>
          printLine("Server failed and killed socket. Should complete promise.") *>
            p.succeed(()).unit
        }
      },
      contract("Application where client app fails")(p =>
        Http.collectZIO[WebSocketChannelEvent] {
          case ChannelEvent(_, UserEventTriggered(UserEvent.HandshakeComplete)) => ZIO.unit
          case ChannelEvent(_, ChannelUnregistered)                             =>
            printLine("Client failed and killed socket. Should complete promise.") *>
              p.succeed(()).unit
        },
      ) { _ =>
        Http.collectZIO[WebSocketChannelEvent] {
          case ChannelEvent(_, UserEventTriggered(UserEvent.HandshakeComplete)) =>
            ZIO.fail(new Exception("Broken client"))
        }
      },
    )

  private def contract(
    name: String,
  )(
    serverApp: Promise[Throwable, Unit] => Http[Any, Throwable, WebSocketChannelEvent, Unit],
  )(clientApp: Promise[Throwable, Unit] => Http[Any, Throwable, WebSocketChannelEvent, Unit]) = {
    suite(name)(
      test("Live") {
        for {
          portAndPromise <- liveServerSetup(serverApp)
          (port, promise) = portAndPromise
          response <- ZIO.serviceWithZIO[Client](
            _.socket(s"ws://localhost:$port/", clientApp(promise).toSocketApp),
          )
          _        <- promise.await.timeout(10.seconds)
        } yield assertTrue(response.status == Status.SwitchingProtocols)
      }.provide(Client.default, Scope.default, TestServer.layer, NettyDriver.default, ServerConfig.liveOnOpenPort),
      test("Test") {
        for {
          portAndPromise <- testServerSetup(serverApp)
          (port, promise) = portAndPromise
          response <- ZIO.serviceWithZIO[Client](
            _.socket(s"ws://localhost:$port/", clientApp(promise).toSocketApp),
          )
          _        <- promise.await.timeout(10.seconds)
        } yield assertTrue(response.status == Status.SwitchingProtocols)
      }.provide(TestClient.layer, Scope.default),
    )
  }

  private def liveServerSetup(
    serverApp: Promise[Throwable, Unit] => Http[Any, Throwable, WebSocketChannelEvent, Unit],
  ) =
    ZIO.serviceWithZIO[Server](server =>
      for {
        p <- Promise.make[Throwable, Unit]
        _ <- server.install(serverApp(p).toSocketApp.toHttp)
      } yield (server.port, p),
    )

  private def testServerSetup(
    serverApp: Promise[Throwable, Unit] => Http[Any, Throwable, WebSocketChannelEvent, Unit],
  ) =
    for {
      p <- Promise.make[Throwable, Unit]
      _ <- TestClient.installSocketApp(serverApp(p))
    } yield (0, p)

}
