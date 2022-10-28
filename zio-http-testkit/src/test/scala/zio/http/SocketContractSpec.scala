package zio.http

import zio._
import zio.http.ChannelEvent.{ChannelRead, ChannelUnregistered, UserEvent, UserEventTriggered}
import zio.http.ServerConfig.LeakDetectionLevel
import zio.http.model.Status
import zio.http.netty.server.NettyDriver
import zio.http.socket._
import zio.test._

object SocketContractSpec extends ZIOSpecDefault {
  val testServerConfig: ZLayer[Any, Nothing, ServerConfig] =
    ZLayer.succeed(ServerConfig.default.port(0).leakDetection(LeakDetectionLevel.PARANOID))

  val severTestLayer = testServerConfig >+> Server.live

  val messageFilter: Http[Any, Nothing, WebSocketChannelEvent, (Channel[WebSocketFrame], String)] =
    Http.collect[WebSocketChannelEvent] { case ChannelEvent(channel, ChannelRead(WebSocketFrame.Text(message))) =>
      (channel, message)
    }

  val messageSocketServer: Http[Any, Throwable, WebSocketChannelEvent, Unit] = messageFilter >>>
    Http.collectZIO[(WebSocketChannel, String)] {
      case (ch, text) if text.contains("Hi Server") =>
        ZIO.debug("Server got message: " + text) *> ch.close()
      case (ch, text)                               => // TODO remove?
        ZIO.debug("Unrecognized message sent to server: " + text)
    }

  def channelSocketServer(p: Promise[Throwable, Unit]): Http[Any, Throwable, WebSocketChannelEvent, Unit] =
    Http.collectZIO[WebSocketChannelEvent] {
      case ChannelEvent(ch, UserEventTriggered(UserEvent.HandshakeComplete)) =>
        ch.writeAndFlush(WebSocketFrame.text("Hi Client"))

      case ChannelEvent(_, ChannelRead(WebSocketFrame.Close(status, reason))) =>
        p.succeed(()) *>
          Console.printLine("Closing channel with status: " + status + " and reason: " + reason)
      case ChannelEvent(_, ChannelUnregistered)                               =>
        p.succeed(()) *>
          Console.printLine("Server Channel unregistered")
      case ChannelEvent(ch, ChannelRead(WebSocketFrame.Text("Hi Server")))    =>
        ch.write(WebSocketFrame.text("Hi Client"))

      case ChannelEvent(ch, other) =>
        Console.printLine("Server Other: " + other)
    }

  val protocol = SocketProtocol.default.withSubProtocol(Some("json"))

  val decoder = SocketDecoder.default.withExtensions(allowed = true)

  def socketAppServer(p: Promise[Throwable, Unit]): SocketApp[Any] =
    (messageSocketServer ++ channelSocketServer(p)).toSocketApp
      .withDecoder(decoder)
      .withProtocol(protocol)

  sys.props.put("ZIOHttpLogLevel", "DEBUG")
  def spec =
    suite("SocketOps")(
      contract(
        "Live",
        ZIO.serviceWithZIO[Server](server =>
          for {
            p <- Promise.make[Throwable, Unit]
            _ <- server.install(socketAppServer(p).toHttp)

          } yield (server.port, p),
        ),
      ).provide(Client.default, Scope.default, TestServer.layer, NettyDriver.default, ServerConfig.liveOnOpenPort),
      contract(
        "Test", {
          for {
            p <- Promise.make[Throwable, Unit]
            _ <- TestClient.addSocketApp(socketAppServer(p))

          } yield (0, p)
        },
      )
        .provide(TestClient.layer, Scope.default),
    )

  def contract[R](name: String, serverSetup: ZIO[R, Nothing, (Int, Promise[Throwable, Unit])]) =
    test(name) {
      val messageSocketClient: Http[Any, Throwable, WebSocketChannelEvent, Unit] = messageFilter >>>
        Http.collectZIO[(WebSocketChannel, String)] {
          case (ch, text) if text.contains("Hi Client") =>
            ch.writeAndFlush(WebSocketFrame.text("Hi Server"), await = true).debug("Client got message: " + text)
        }

      val channelSocketClient: Http[Any, Throwable, WebSocketChannelEvent, Unit] =
        Http.collectZIO[WebSocketChannelEvent] {
          case ChannelEvent(_, ChannelUnregistered) =>
            Console.printLine("Client Channel unregistered")

          case ChannelEvent(ch, other) =>
            Console.printLine("Client received other event: " + other)
        }

      val httpSocketClient: Http[Any, Throwable, WebSocketChannelEvent, Unit] =
        messageSocketClient ++ channelSocketClient

      val socketAppClient: SocketApp[Any] =
        httpSocketClient.toSocketApp
          .withDecoder(decoder)
          .withProtocol(protocol)

      for {
        portAndPromise <- serverSetup
        response       <- ZIO.serviceWithZIO[Client](_.socket(s"ws://localhost:${portAndPromise._1}/", socketAppClient))
        _              <- portAndPromise._2.await
      } yield assertTrue(response.status == Status.SwitchingProtocols)
    }

}
