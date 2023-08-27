package example

import zio._

import zio.http.ChannelEvent.{Read, UserEvent, UserEventTriggered}
import zio.http._

object WebSocketSimpleClient extends ZIOAppDefault {

  val url = "ws://ws.vi-server.org/mirror"

  val socketApp: WebSocketApp[Any] =
    Handler

      // Listen for all websocket channel events
      .webSocket { channel =>
        channel.receiveAll {

          // Send a "foo" message to the server once the connection is established
          case UserEventTriggered(UserEvent.HandshakeComplete) =>
            channel.send(Read(WebSocketFrame.text("foo")))

          // Send a "bar" if the server sends a "foo"
          case Read(WebSocketFrame.Text("foo"))                =>
            channel.send(Read(WebSocketFrame.text("bar")))

          // Close the connection if the server sends a "bar"
          case Read(WebSocketFrame.Text("bar"))                =>
            ZIO.succeed(println("Goodbye!")) *> channel.send(Read(WebSocketFrame.close(1000)))

          case _ =>
            ZIO.unit
        }
      }

  val app: ZIO[Client with Scope, Throwable, Response] =
    socketApp.connect(url) *> ZIO.never

  val run: ZIO[Any, Throwable, Any] =
    app.provide(Client.default, Scope.default)

}
