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

  val protocol = SocketProtocol.default.withSubProtocol(Some("json"))
  val decoder  = SocketDecoder.default.withExtensions(allowed = true)

  def spec =
    suite("SocketOps")(
            contract("Successful Multi-message application"){
              p =>
              def channelSocketServer: HttpSocket =
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
                    // TODO Only use one of these?
                    p.succeed(()) *>
                      ch.close(true)

                  case ChannelEvent(_, other) =>
                    Console.printLine("Server Unexpected: " + other)
                }

              val messageSocketServer: HttpSocket = messageFilter >>>
                Http.collectZIO[(WebSocketChannel, String)] {
                  case (ch, text) if text.contains("Hi Server") =>
                    ZIO.debug("Server got message: " + text) *> ch.close()
                  case (_, text)                                => // TODO remove?
                    ZIO.debug("Unrecognized message sent to server: " + text)
                }

                messageSocketServer
                  .defaultWith(channelSocketServer)


                }{
              p =>

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
                }
            ,
      contract("Application where server app fails")(
        _ => Http.collectZIO[WebSocketChannelEvent] { case ChannelEvent(ch, UserEventTriggered(UserEvent.HandshakeComplete)) =>
          ZIO.fail(new Exception("Broken server"))
        }
      ){
        p =>
          Http.collectZIO[WebSocketChannelEvent] { case ChannelEvent(ch, ChannelUnregistered) =>
            Console.printLine("Server failed and killed socket. Should complete promise.") *>
              p.succeed(()).unit
          }
      }
    )


  def contract[R](name: String)(serverApp: Promise[Throwable, Unit] => HttpSocket)(clientApp: Promise[Throwable, Unit] => HttpSocket) = {
    suite(name) (
      test("Live") {
        for {
          portAndPromise <- liveServerSetup(serverApp)
          response <- ZIO.serviceWithZIO[Client](_.socket(s"ws://localhost:${portAndPromise._1}/", clientApp(portAndPromise._2).toSocketApp))
          _ <- portAndPromise._2.await.timeout(10.seconds)
        } yield assertTrue(response.status == Status.SwitchingProtocols)
      }.provide(Client.default, Scope.default, TestServer.layer, NettyDriver.default, ServerConfig.liveOnOpenPort),
      test("Test") {
        for {
          portAndPromise <- testServerSetup(serverApp)
          response <- ZIO.serviceWithZIO[Client](_.socket(s"ws://localhost:${portAndPromise._1}/", clientApp(portAndPromise._2).toSocketApp))
          _ <- portAndPromise._2.await.timeout(10.seconds)
        } yield assertTrue(response.status == Status.SwitchingProtocols)
      }.provide(TestClient.layer, Scope.default)
    )
  }

  private def liveServerSetup(serverApp: Promise[Throwable, Unit] => HttpSocket) = {
    ZIO.serviceWithZIO[Server](server =>
      for {
        p <- Promise.make[Throwable, Unit]
        _ <- server.install(serverApp(p).toSocketApp.toHttp)
        // TODO Handle these somewhere?
        //                  .withDecoder(decoder)
        //                  .withProtocol(protocol)
      } yield (server.port, p)
    )
  }

  private def testServerSetup(serverApp: Promise[Throwable, Unit] => HttpSocket) =
    for {
      p <- Promise.make[Throwable, Unit]
      _ <- TestClient.addSocketApp(serverApp(p).toSocketApp)
    } yield (0, p)

}
