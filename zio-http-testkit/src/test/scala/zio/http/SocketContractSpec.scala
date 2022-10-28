package zio.http

import zio._
import zio.http.model.{Method, Status}
import zio.http.ChannelEvent.{ChannelRead, UserEvent, UserEventTriggered}
import zio.http.ServerConfig.LeakDetectionLevel
import zio.http.netty.server.NettyDriver
import zio.http.socket.{SocketApp, SocketDecoder, SocketProtocol, WebSocketChannel, WebSocketChannelEvent, WebSocketFrame}
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
    }

  val channelSocketServer
  : Http[Any, Throwable, WebSocketChannelEvent, Unit] =
    Http.collectZIO[WebSocketChannelEvent] {
      case ChannelEvent(ch, UserEventTriggered(UserEvent.HandshakeComplete))  =>
        ch.writeAndFlush(WebSocketFrame.text("Hi Client"))

      case ChannelEvent(_, ChannelRead(WebSocketFrame.Close(status, reason))) =>
        Console.printLine("Closing channel with status: " + status + " and reason: " + reason)
    }

  val httpSocketServer: Http[Any, Throwable, WebSocketChannelEvent, Unit] =
    messageSocketServer ++ channelSocketServer

  val protocol = SocketProtocol.default.withSubProtocol(Some("json")) // Setup protocol settings

  val decoder = SocketDecoder.default.withExtensions(allowed = true) // Setup decoder settings

  val socketAppServer: SocketApp[Any] = // Combine all channel handlers together
    httpSocketServer.toSocketApp
      .withDecoder(decoder)   // Setup websocket decoder config
      .withProtocol(protocol) // Setup websocket protocol config

  def spec =
    suite("SocketOps")(

      contract("Live",
        ZIO.serviceWithZIO[Server](_.install(socketAppServer.toHttp))
      )
        .provide(Client.default, Scope.default, TestServer.layer, NettyDriver.default, ServerConfig.liveOnOpenPort),

      contract("Test", TestClient.addSocketApp(socketAppServer))
        .provide(TestClient.layer, Scope.default),
    )

  def contract[R](name: String, serverSetup: ZIO[R, Nothing, Unit] = ZIO.unit) =
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
        _ <- serverSetup
        response <- ZIO.serviceWithZIO[Client](_.socket("https://localhost:3000/", socketAppClient))
        //            _ <- client.
      } yield assertCompletes
    }


  import zio._
  import zio.http._
  import DynamicServer.Id
  import zio.http.model.Scheme

  import java.util.UUID

  sealed trait DynamicServer {
    def add(app: HttpApp[Any, Throwable]): UIO[Id]

    def get(id: Id): UIO[Option[HttpApp[Any, Throwable]]]

    def port: ZIO[Any, Nothing, Int]

    def setStart(n: Server): UIO[Boolean]

    def start: IO[Nothing, Server]
  }

  object DynamicServer {

    type Id = String

    val APP_ID = "X-APP_ID"

    def app: HttpApp[DynamicServer, Throwable] = Http
      .fromOptionFunction[Request] { req =>
        for {
          id  <- req.headerValue(APP_ID) match {
            case Some(id) => ZIO.succeed(id)
            case None     => ZIO.fail(None)
          }
          app <- get(id)
          res <- app match {
            case Some(app) => app(req)
            case None      => ZIO.fail(None)
          }
        } yield res
      }

    def baseURL(scheme: Scheme): ZIO[DynamicServer, Nothing, String] =
      port.map(port => s"${scheme.encode}://localhost:$port")

    def deploy[R](app: HttpApp[R, Throwable]): ZIO[DynamicServer with R, Nothing, String] =
      for {
        env <- ZIO.environment[R]
        id  <- ZIO.environmentWithZIO[DynamicServer](_.get.add(app.provideEnvironment(env)))
      } yield id

    def get(id: Id): ZIO[DynamicServer, Nothing, Option[HttpApp[Any, Throwable]]] =
      ZIO.environmentWithZIO[DynamicServer](_.get.get(id))

    def httpURL: ZIO[DynamicServer, Nothing, String] = baseURL(Scheme.HTTP)

    def live: ZLayer[Any, Nothing, DynamicServer] =
      ZLayer {
        for {
          ref <- Ref.make(Map.empty[Id, HttpApp[Any, Throwable]])
          pr  <- Promise.make[Nothing, Server]
        } yield new Live(ref, pr)
      }

    def port: ZIO[DynamicServer, Nothing, Int] = ZIO.environmentWithZIO[DynamicServer](_.get.port)

    def setStart(s: Server): ZIO[DynamicServer, Nothing, Boolean] =
      ZIO.environmentWithZIO[DynamicServer](_.get.setStart(s))

    def start: ZIO[DynamicServer, Nothing, Server] = ZIO.environmentWithZIO[DynamicServer](_.get.start)

    def wsURL: ZIO[DynamicServer, Nothing, String] = baseURL(Scheme.WS)

    final class Live(ref: Ref[Map[Id, HttpApp[Any, Throwable]]], pr: Promise[Nothing, Server]) extends DynamicServer {
      def add(app: HttpApp[Any, Throwable]): UIO[Id] = for {
        id <- ZIO.succeed(UUID.randomUUID().toString)
        _  <- ref.update(map => map + (id -> app))
      } yield id

      def get(id: Id): UIO[Option[HttpApp[Any, Throwable]]] = ref.get.map(_.get(id))

      def port: ZIO[Any, Nothing, Int] = start.map(_.port)

      def setStart(s: Server): UIO[Boolean] = pr.complete(ZIO.attempt(s).orDie)

      def start: IO[Nothing, Server] = pr.await
    }
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
