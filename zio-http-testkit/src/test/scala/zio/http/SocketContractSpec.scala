package zio.http

import zio._
import zio.http.model.{Method, Status}
import zio.http.ChannelEvent.{ChannelRead, UserEvent, UserEventTriggered}
import zio.http.ServerConfig.LeakDetectionLevel
import zio.http.netty.server.NettyDriver
import zio.http.socket.{SocketApp, SocketDecoder, SocketProtocol, WebSocketChannel, WebSocketChannelEvent, WebSocketFrame}
import zio.test.TestAspect.ignore
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
      case (ch, text) => // TODO remove
        ZIO.debug("Unrecognized message sent to server: " + text)
    }

  val channelSocketServer
  : Http[Any, Throwable, WebSocketChannelEvent, Unit] =
    Http.collectZIO[WebSocketChannelEvent] {
      case ChannelEvent(ch, UserEventTriggered(UserEvent.HandshakeComplete))  =>
        ZIO.debug("Server got a complete handshake") *>
        ch.writeAndFlush(WebSocketFrame.text("Hi Client"))

      case ChannelEvent(_, ChannelRead(WebSocketFrame.Close(status, reason))) =>
        Console.printLine("Closing channel with status: " + status + " and reason: " + reason)
      case ChannelEvent(ch, other) =>
        Console.printLine("Server Other: " + other) *> ch.write(WebSocketFrame.text("Hi Client")).tapError(Console.printLine(_))
    }

  val httpSocketServer: Http[Any, Throwable, WebSocketChannelEvent, Unit] =
    messageSocketServer ++ channelSocketServer

  val protocol = SocketProtocol.default.withSubProtocol(Some("json")) // Setup protocol settings

  val decoder = SocketDecoder.default.withExtensions(allowed = true) // Setup decoder settings

  val socketAppServer: SocketApp[Any] = // Combine all channel handlers together
    httpSocketServer.toSocketApp
      .withDecoder(decoder)   // Setup websocket decoder config
      .withProtocol(protocol) // Setup websocket protocol config

  sys.props.put("ZIOHttpLogLevel", "DEBUG")
  def spec =
    suite("SocketOps")(

      contract("Live",
        ZIO.serviceWithZIO[Server](server => server.install(socketAppServer.toHttp).as(server.port))
      )
        .provide(Client.default, Scope.default, TestServer.layer, NettyDriver.default, ServerConfig.liveOnOpenPort),

      contract("Test", TestClient.addSocketApp(socketAppServer).as(0))
        .provide(TestClient.layer, Scope.default) @@ ignore,
    )

  def contract[R](name: String, serverSetup: ZIO[R, Nothing, Int]) =
    test(name) {
      val messageSocketClient: Http[Any, Throwable, WebSocketChannelEvent, Unit] = messageFilter >>>
        Http.collectZIO[(WebSocketChannel, String)] {
          case (ch, text) if text.contains("Hi Client") =>
            ch.writeAndFlush(WebSocketFrame.text("Hi Server"), await = true).debug("Client got message: " + text)
        }

      val channelSocketClient: Http[Any, Throwable, WebSocketChannelEvent, Unit] = Http.empty

      val httpSocketClient: Http[Any, Throwable, WebSocketChannelEvent, Unit] =
        messageSocketClient ++ channelSocketClient


      val socketAppClient: SocketApp[Any] = // Combine all channel handlers together
        httpSocketClient.toSocketApp
          .withDecoder(decoder)   // Setup websocket decoder config
          .withProtocol(protocol) // Setup websocket protocol config

      for {
        port <- serverSetup
        response <- ZIO.serviceWithZIO[Client](_.socket(s"ws://localhost:$port/", socketAppClient))
        //            _ <- client.
      } yield assertCompletes
    }


}


/*
    Server
      - SocketOpened
        - write("Hi client")

      - MessageReceived("Hi Server")
        - close(channel)

    Client
      - MessageReceived
        - write("Hi Server")

 */
