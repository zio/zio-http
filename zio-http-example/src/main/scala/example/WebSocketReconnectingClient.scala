package example

import zio._
import zio.duration._
import zio.logging._
import zio.http._
import zio.http.ChannelEvent.{ExceptionCaught, Read, UserEvent, UserEventTriggered}

object WebSocketReconnectingClient extends zio.App {

  val url = "ws://ws.vi-server.org/mirror"

  // A promise is used to be able to notify the application about WebSocket errors
  def makeSocketApp(p: Promise[Throwable, Throwable]): WebSocketApp[Any] =
    Handler
      .webSocket { channel =>
        channel.receiveAll {
          // On connect send a "foo" message to the server to start the echo loop
          case UserEventTriggered(UserEvent.HandshakeComplete) =>
            channel.send(ChannelEvent.Read(WebSocketFrame.text("foo")))
          // On receiving "foo", reply with another "foo" to keep the echo loop going
          case Read(WebSocketFrame.Text("foo")) =>
            log.info("Received foo message.") *>
              ZIO.sleep(1.second) *>
              channel.send(ChannelEvent.Read(WebSocketFrame.text("foo")))
          // Handle exception and convert it to failure to signal the shutdown of the socket connection via the promise
          case ExceptionCaught(t) =>
            p.fail(t)
          case _ =>
            ZIO.unit
        }
      }
      .tapError { f =>
        // signal failure to the application
        p.succeed(f)
      }

  val app: ZIO[Logging with Client with Clock with Promise[Throwable, Throwable], Throwable, Unit] =
    for {
      p <- zio.Promise.make[Throwable, Throwable]
      _ <- makeSocketApp(p)
             .connect(url)
             .catchAll(t =>
               // Convert a failed connection attempt to an error to trigger a reconnect
               p.fail(t)
             )
      f <- p.await
      _ <- log.error(s"App failed: $f")
      _ <- log.error(s"Trying to reconnect...")
      _ <- ZIO.sleep(1.second)
    } yield ()

  override def run(args: List[String]): ZIO[zio.ZEnv, Nothing, ExitCode] =
    app
      .provideSomeLayer[zio.ZEnv](Console.live ++ HttpClientZioBackend.layer() ++ Clock.live)
      .exitCode
}
