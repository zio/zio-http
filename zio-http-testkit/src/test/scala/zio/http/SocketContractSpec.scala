package zio.http

import zio._
import zio.http.ChannelEvent.{ChannelRead, ChannelUnregistered, UserEvent, UserEventTriggered}
import zio.http.model.Status
import zio.http.netty.server.NettyDriver
import zio.http.socket._
import zio.test.TestAspect.ignore
import zio.test._

object SocketContractSpec extends ZIOSpecDefault {

  val messageFilter: Http[Any, Nothing, WebSocketChannelEvent, (Channel[WebSocketFrame], String)] =
    Http.collect[WebSocketChannelEvent] { case ChannelEvent(channel, ChannelRead(WebSocketFrame.Text(message))) =>
      (channel, message)
    }

  val messageSocketServer: HttpSocket = messageFilter >>>
    Http.collectZIO[(WebSocketChannel, String)] {
      case (ch, text) if text.contains("Hi Server") =>
        ZIO.debug("Server got message: " + text) *> ch.close()
      case (_, text)                                => // TODO remove?
        ZIO.debug("Unrecognized message sent to server: " + text)
    }

  def channelSocketServer(p: Promise[Throwable, Unit]): HttpSocket =
    Http.collectZIO[WebSocketChannelEvent] {
      case ChannelEvent(ch, UserEventTriggered(UserEvent.HandshakeComplete)) =>
        ch.write(WebSocketFrame.text("junk")) *>
          ch.writeAndFlush(WebSocketFrame.text("Hi Client"))

      case ChannelEvent(_, ChannelRead(WebSocketFrame.Close(status, reason))) =>
        p.succeed(()) *>
          Console.printLine("Closing channel with status: " + status + " and reason: " + reason)
      case ChannelEvent(_, ChannelUnregistered)                               =>
        p.succeed(()) *>
          Console.printLine("Server Channel unregistered")
      case ChannelEvent(ch, ChannelRead(WebSocketFrame.Text("Hi Server")))    =>
        // TODO Only use one of these?
        p.succeed(()) *>
          ch.close(true)
//        ch.write(WebSocketFrame.text("Hi Client"))

      case ChannelEvent(_, other) =>
        Console.printLine("Server Unexpected: " + other)
    }

  private val failServer: HttpSocket =
    Http.collectZIO[WebSocketChannelEvent] { case ChannelEvent(ch, UserEventTriggered(UserEvent.HandshakeComplete)) =>
      ZIO.fail(new Exception("Broken server"))
//        ZIO.unit
    }

  val protocol = SocketProtocol.default.withSubProtocol(Some("json"))
  val decoder  = SocketDecoder.default.withExtensions(allowed = true)

  def socketAppServer(p: Promise[Throwable, Unit]): SocketApp[Any] =
    messageSocketServer
      .defaultWith(channelSocketServer(p))
      .toSocketApp
      .withDecoder(decoder)
      .withProtocol(protocol)

  def spec =
    suite("SocketOps")(
      suite("Successful Multi-message application")(
        happySocketApp(
          "Live",
          ZIO.serviceWithZIO[Server](server =>
            for {
              p <- Promise.make[Throwable, Unit]
              _ <- server.install(socketAppServer(p).toHttp)

            } yield (server.port, p),
          ),
        ).provide(Client.default, Scope.default, TestServer.layer, NettyDriver.default, ServerConfig.liveOnOpenPort),
        happySocketApp(
          "Test", {
            for {
              p <- Promise.make[Throwable, Unit]
              _ <- TestClient.addSocketApp(socketAppServer(p))

            } yield (0, p)
          },
        )
          .provide(TestClient.layer, Scope.default),
      ),
      suite("Application where server app fails")(
        handlesAServerFailure(
          "Live",
          ZIO.serviceWithZIO[Server](server =>
            for {
              p <- Promise.make[Throwable, Unit]
              _ <- server.install(failServer.toSocketApp.toHttp)

            } yield (server.port, p),
          ),
        ).provide(Client.default, Scope.default, TestServer.layer, NettyDriver.default, ServerConfig.liveOnOpenPort),
        handlesAServerFailure(
          "Test", {
            for {
              p <- Promise.make[Throwable, Unit]
              _ <- TestClient.addSocketApp(failServer.toSocketApp)

            } yield (0, p)
          },
        )
          .provide(TestClient.layer, Scope.default) @@ ignore,
      ),
    )

  def happySocketApp[R](name: String, serverSetup: ZIO[R, Nothing, (Int, Promise[Throwable, Unit])]) =
    test(name) {
      val messageSocketClient: HttpSocket = messageFilter >>>
        Http.collectZIO[(WebSocketChannel, String)] {
          case (ch, text) if text.contains("junk")      =>
            ZIO.fail(new Exception("boom"))
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

      val httpSocketClient: HttpSocket =
        messageSocketClient.defaultWith(channelSocketClient)

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

  def handlesAServerFailure[R](name: String, serverSetup: ZIO[R, Nothing, (Int, Promise[Throwable, Unit])]) =
    test(name) {
      def channelSocketClient(p: Promise[Throwable, Unit]): HttpSocket =
        Http.collectZIO[WebSocketChannelEvent] { case ChannelEvent(ch, ChannelUnregistered) =>
          Console.printLine("Server failed and killed socket. Should complete promise.") *>
            p.succeed(()).unit
        }

      def socketAppClient(p: Promise[Throwable, Unit]): SocketApp[Any] =
        channelSocketClient(p).toSocketApp

      for {
        portAndPromise <- serverSetup
        response       <- ZIO.serviceWithZIO[Client](
          _.socket(s"ws://localhost:${portAndPromise._1}/", socketAppClient(portAndPromise._2)),
        )
        _              <- portAndPromise._2.await
      } yield assertTrue(response.status == Status.SwitchingProtocols)
    }

}
