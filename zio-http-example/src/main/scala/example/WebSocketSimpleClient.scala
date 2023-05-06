package example

import zio._

import zio.http.ChannelEvent.{ChannelRead, UserEvent, UserEventTriggered}
import zio.http.socket.{WebSocketChannel, WebSocketChannelEvent, WebSocketFrame}
import zio.http.{ChannelEvent, Client, Http, Response}

object WebSocketSimpleClient extends ZIOAppDefault {

  val url = "ws://ws.vi-server.org/mirror"

  val httpSocket: Http[Any, Throwable, WebSocketChannel, Unit] =
    Http

      // Listen for all websocket channel events
      .collectZIO[WebSocketChannel] { case channel =>
        channel.receive.flatMap {

          // Send a "foo" message to the server once the connection is established
          case UserEventTriggered(UserEvent.HandshakeComplete) =>
            channel.send(ChannelRead(WebSocketFrame.text("foo")))

          // Send a "bar" if the server sends a "foo"
          case ChannelRead(WebSocketFrame.Text("foo"))         =>
            channel.send(ChannelRead(WebSocketFrame.text("bar")))

          // Close the connection if the server sends a "bar"
          case ChannelRead(WebSocketFrame.Text("bar"))         =>
            ZIO.succeed(println("Goodbye!")) *> channel.send(ChannelRead(WebSocketFrame.close(1000)))
        }.forever
      }

  val app: ZIO[Client with Scope, Throwable, Response] =
    httpSocket.toSocketApp.connect(url) *> ZIO.never

  val run: ZIO[ZIOAppArgs with Scope, Throwable, Any] =
    ZIO.scoped {
      app.provideSome[Scope](Client.default)
    }

}
