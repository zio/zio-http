package example

import zhttp.http.{Http, Response}
import zhttp.service.ChannelEvent
import zhttp.service.ChannelEvent.{ChannelRead, UserEvent, UserEventTriggered}
import zhttp.socket.{WebSocketChannelEvent, WebSocketFrame}
import zio._

object WebSocketSimpleClient extends ZIOAppDefault {

  // Setup client envs
  val env = Scope.default

  val url = "ws://ws.vi-server.org/mirror"

  val httpSocket: Http[Any, Throwable, WebSocketChannelEvent, Unit] =
    Http

      // Listen for all websocket channel events
      .collectZIO[WebSocketChannelEvent] {

        // Send a "foo" message to the server once the connection is established
        case ChannelEvent(ch, UserEventTriggered(UserEvent.HandshakeComplete)) =>
          ch.writeAndFlush(WebSocketFrame.text("foo"))

        // Send a "bar" if the server sends a "foo"
        case ChannelEvent(ch, ChannelRead(WebSocketFrame.Text("foo")))         =>
          ch.writeAndFlush(WebSocketFrame.text("bar"))

        // Close the connection if the server sends a "bar"
        case ChannelEvent(ch, ChannelRead(WebSocketFrame.Text("bar")))         =>
          ZIO.succeed(println("Goodbye!")) *> ch.writeAndFlush(WebSocketFrame.close(1000))
      }

  val app: ZIO[Scope, Throwable, Response] =
    httpSocket.toSocketApp.connect(url)

  val run = app.provideLayer(env)

}
