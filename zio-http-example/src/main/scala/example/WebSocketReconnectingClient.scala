package example

import zio._

import zio.http.ChannelEvent.{ExceptionCaught, Read, UserEvent, UserEventTriggered}
import zio.http._

object WebSocketReconnectingClient extends ZIOAppDefault {

  val url = "ws://ws.vi-server.org/mirror"

  // A promise is used to be able to notify application about websocket errors
  def makeSocketApp(p: Promise[Nothing, Throwable]): WebSocketApp[Any] =
    Handler

      // Listen for all websocket channel events
      .webSocket { channel =>
        channel.receiveAll {

          // On connect send a "foo" message to the server to start the echo loop
          case UserEventTriggered(UserEvent.HandshakeComplete) =>
            channel.send(ChannelEvent.Read(WebSocketFrame.text("foo")))

          // On receiving "foo", we'll reply with another "foo" to keep echo loop going
          case Read(WebSocketFrame.Text("foo"))                =>
            ZIO.logInfo("Received foo message.") *>
              ZIO.sleep(1.second) *>
              channel.send(ChannelEvent.Read(WebSocketFrame.text("foo")))

          // Handle exception and convert it to failure to signal the shutdown of the socket connection via the promise
          case ExceptionCaught(t)                              =>
            ZIO.fail(t)

          case _ =>
            ZIO.unit
        }
      }.tapErrorZIO { f =>
        // signal failure to application
        p.succeed(f)
      }

  val app: ZIO[Any with Client with Scope, Throwable, Unit] = {
    (for {
      p <- zio.Promise.make[Nothing, Throwable]
      _ <- makeSocketApp(p).connect(url).catchAll { t =>
        // convert a failed connection attempt to an error to trigger a reconnect
        p.succeed(t)
      }
      f <- p.await
      _ <- ZIO.logError(s"App failed: $f")
      _ <- ZIO.logError(s"Trying to reconnect...")
      _ <- ZIO.sleep(1.seconds)
    } yield {
      ()
    }) *> app
  }

  val run =
    app.provide(Client.default, Scope.default)

}
