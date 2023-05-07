package zio.http

import zio.Console.printLine
import zio._
import zio.test._

import zio.http.ChannelEvent.{Read, Unregistered, UserEvent, UserEventTriggered}
import zio.http.netty.server.NettyDriver
import zio.http.{Headers, Status, Version}

object SocketContractSpec extends ZIOSpecDefault {

  def spec: Spec[Any, Any] =
    suite("SocketOps")(
      contract("Successful Multi-message application") { p =>
        val socketServer: Http[Any, Throwable, WebSocketChannel, Unit] =
          Http.webSocket { channel =>
            channel.receive.flatMap {
              case Read(WebSocketFrame.Text("Hi Server"))          =>
                printLine("Server got message: Hi Server") *> channel.shutdown
              case UserEventTriggered(UserEvent.HandshakeComplete) =>
                channel.send(ChannelEvent.Read(WebSocketFrame.text("Hi Client")))
              case Unregistered                                    =>
                p.succeed(()) *>
                  printLine("Server Channel unregistered")
              case other                                           =>
                printLine("Server Unexpected: " + other)
            }.forever
          }

        socketServer
      } { _ =>
        val socketClient: Http[Any, Throwable, WebSocketChannel, Unit] =
          Http.webSocket { channel =>
            channel.receive.flatMap {
              case ChannelEvent.Read(WebSocketFrame.Text("Hi Client")) =>
                channel.send(Read(WebSocketFrame.text("Hi Server")))
              case ChannelEvent.Unregistered                           =>
                printLine("Client Channel unregistered")
              case other                                               =>
                printLine("Client received Unexpected event: " + other)
            }.forever
          }

        socketClient
      },
      contract("Application where server app fails")(_ =>
        Http.webSocket { channel =>
          channel.receive.flatMap {
            case UserEventTriggered(UserEvent.HandshakeComplete) =>
              ZIO.fail(new Exception("Broken server"))
            case _                                               =>
              ZIO.unit
          }.forever
            .ensuring(channel.shutdown)
        },
      ) { p =>
        Http.webSocket { channel =>
          channel.receive.flatMap {
            case Unregistered =>
              printLine("Server failed and killed socket. Should complete promise.") *>
                p.succeed(()).unit
            case _            =>
              ZIO.unit
          }.forever
        }
      },
      contract("Application where client app fails")(p =>
        Http.webSocket { channel =>
          channel.receive.flatMap {
            case Unregistered =>
              printLine("Client failed and killed socket. Should complete promise.") *>
                p.succeed(()).unit
            case _            =>
              ZIO.unit
          }.forever
        },
      ) { _ =>
        Http.webSocket { channel =>
          channel.receive.flatMap {
            case UserEventTriggered(UserEvent.HandshakeComplete) =>
              ZIO.fail(new Exception("Broken client"))
            case _                                               => ZIO.unit
          }.forever
            .ensuring(channel.shutdown)
        }
      },
    )

  private def contract(
    name: String,
  )(
    serverApp: Promise[Throwable, Unit] => Http[Any, Throwable, WebSocketChannel, Unit],
  )(clientApp: Promise[Throwable, Unit] => Http[Any, Throwable, WebSocketChannel, Unit]) = {
    suite(name)(
      test("Live") {
        for {
          portAndPromise <- liveServerSetup(serverApp)
          (port, promise) = portAndPromise
          url      <- ZIO.fromEither(URL.decode(s"ws://localhost:$port/")).orDie
          response <- ZIO.serviceWithZIO[Client](
            _.socket(Version.Http_1_1, url, Headers.empty, clientApp(promise).toSocketApp),
          )
          _        <- promise.await.timeout(10.seconds)
        } yield assertTrue(response.status == Status.SwitchingProtocols)
      }.provideSome[Client](
        TestServer.layer,
        NettyDriver.live,
        ZLayer.succeed(Server.Config.default.onAnyOpenPort),
        Scope.default,
      ).provide(Client.default),
      test("Test") {
        for {
          portAndPromise <- testServerSetup(serverApp)
          (port, promise) = portAndPromise
          url      <- ZIO.fromEither(URL.decode(s"ws://localhost:$port/")).orDie
          response <- ZIO.serviceWithZIO[Client](
            _.socket(Version.Http_1_1, url, Headers.empty, clientApp(promise).toSocketApp),
          )
          _        <- promise.await.timeout(10.seconds)
        } yield assertTrue(response.status == Status.SwitchingProtocols)
      }.provide(TestClient.layer, Scope.default),
    )
  }

  private def liveServerSetup(
    serverApp: Promise[Throwable, Unit] => Http[Any, Throwable, WebSocketChannel, Unit],
  ): ZIO[Server, Nothing, (RuntimeFlags, Promise[Throwable, Unit])] =
    ZIO.serviceWithZIO[Server](server =>
      for {
        p <- Promise.make[Throwable, Unit]
        _ <- server.install(serverApp(p).toSocketApp.toRoute)
      } yield (server.port, p),
    )

  private def testServerSetup(
    serverApp: Promise[Throwable, Unit] => Http[Any, Throwable, WebSocketChannel, Unit],
  ): ZIO[TestClient, Nothing, (RuntimeFlags, Promise[Throwable, Unit])] =
    for {
      p <- Promise.make[Throwable, Unit]
      _ <- TestClient.installSocketApp(serverApp(p))
    } yield (0, p)

}
