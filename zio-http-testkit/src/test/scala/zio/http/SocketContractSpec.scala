package zio.http

import zio._
import zio.http.ChannelEvent.{ChannelRead, ChannelUnregistered, UserEvent, UserEventTriggered}
import zio.http.model.Status
import zio.http.netty.server.NettyDriver
import zio.http.socket._
import zio.test.TestAspect.ignore
import zio.test._

object SocketContractSpec extends ZIOSpecDefault {

  private val messageFilter: Http[Any, Nothing, WebSocketChannelEvent, (Channel[WebSocketFrame], String)] =
    Http.collect[WebSocketChannelEvent] { case ChannelEvent(channel, ChannelRead(WebSocketFrame.Text(message))) =>
      (channel, message)
    }

  private val warnOnUnrecognizedEvent = Http.collectZIO[WebSocketChannelEvent]{
    case other =>
      ZIO.fail(new Exception("Unexpected event: " + other))
  }

  def spec =
    suite("SocketOps")(
      contract("Successful Multi-message application") { p =>
        def channelSocketServer: HttpSocket =
          Http.collectZIO[WebSocketChannelEvent] {
            case ChannelEvent(ch, UserEventTriggered(UserEvent.HandshakeComplete)) =>
              ch.writeAndFlush(WebSocketFrame.text("Hi Client"))
            case ChannelEvent(_, ChannelUnregistered)                               =>
              p.succeed(()) *>
                Console.printLine("Server Channel unregistered")
            case ChannelEvent(ch, ChannelRead(WebSocketFrame.Text("Hi Server")))    =>
                ch.close()
            case ChannelEvent(_, other) =>
              Console.printLine("Server Unexpected: " + other)
          }.defaultWith(warnOnUnrecognizedEvent)

        val messageSocketServer: HttpSocket = messageFilter >>>
          Http.collectZIO[(WebSocketChannel, String)] {
            case (ch, text) if text.contains("Hi Server") =>
              ZIO.debug("Server got message: " + text) *> ch.close()
          }

        messageSocketServer
          .defaultWith(channelSocketServer)
      } { p =>
        val messageSocketClient: HttpSocket = messageFilter >>>
          Http.collectZIO[(WebSocketChannel, String)] {
            case (ch, text) if text.contains("Hi Client") =>
              ch.writeAndFlush(WebSocketFrame.text("Hi Server"), await = true).debug("Client got message: " + text)
          }

        val channelSocketClient: HttpSocket =
          Http.collectZIO[WebSocketChannelEvent] {
            case ChannelEvent(_, ChannelUnregistered) =>
              Console.printLine("Client Channel unregistered")

            case ChannelEvent(_, other) =>
              Console.printLine("Client received Unexpected event: " + other)
          }

        messageSocketClient.defaultWith(channelSocketClient)
      },
      contract("Application where server app fails")(_ =>
        Http.collectZIO[WebSocketChannelEvent] {
          case ChannelEvent(ch, UserEventTriggered(UserEvent.HandshakeComplete)) =>
            ZIO.fail(new Exception("Broken server"))
        },
      ) { p =>
        Http.collectZIO[WebSocketChannelEvent] { case ChannelEvent(ch, ChannelUnregistered) =>
          Console.printLine("Server failed and killed socket. Should complete promise.") *>
            p.succeed(()).unit
        }
      },
      contract("Application where client app fails")(p =>
        Http.collectZIO[WebSocketChannelEvent] {
          case ChannelEvent(ch, UserEventTriggered(UserEvent.HandshakeComplete)) => ZIO.unit
          case ChannelEvent(ch, ChannelUnregistered) =>
            Console.printLine("Client failed and killed socket. Should complete promise.") *>
              p.succeed(()).unit
        },
      ) { p =>
        Http.collectZIO[WebSocketChannelEvent] {
          case ChannelEvent(ch, UserEventTriggered(UserEvent.HandshakeComplete)) =>
            ZIO.fail(new Exception("Broken client"))
        }
      },
    )

  private def contract(
    name: String,
  )(serverApp: Promise[Throwable, Unit] => HttpSocket)(clientApp: Promise[Throwable, Unit] => HttpSocket) = {
    suite(name)(
      test("Live") {
        for {
          portAndPromise <- liveServerSetup(serverApp)
          response       <- ZIO.serviceWithZIO[Client](
            _.socket(s"ws://localhost:${portAndPromise._1}/", clientApp(portAndPromise._2).toSocketApp),
          )
          _              <- portAndPromise._2.await.timeout(10.seconds)
        } yield assertTrue(response.status == Status.SwitchingProtocols)
      }.provide(Client.default, Scope.default, TestServer.layer, NettyDriver.default, ServerConfig.liveOnOpenPort),
      test("Test") {
        for {
          portAndPromise <- testServerSetup(serverApp)
          response       <- ZIO.serviceWithZIO[Client](
            _.socket(s"ws://localhost:${portAndPromise._1}/", clientApp(portAndPromise._2).toSocketApp),
          )
          _              <- portAndPromise._2.await.timeout(10.seconds)
        } yield assertTrue(response.status == Status.SwitchingProtocols)
      }.provide(TestClient.layer, Scope.default),
    )
  }

  private def liveServerSetup(serverApp: Promise[Throwable, Unit] => HttpSocket) = {
    ZIO.serviceWithZIO[Server](server =>
      for {
        p <- Promise.make[Throwable, Unit]
        _ <- server.install(serverApp(p).toSocketApp.toHttp)
      } yield (server.port, p),
    )
  }

  private def testServerSetup(serverApp: Promise[Throwable, Unit] => HttpSocket) =
    for {
      p <- Promise.make[Throwable, Unit]
      _ <- TestClient.addSocketApp(serverApp(p))
    } yield (0, p)

}
