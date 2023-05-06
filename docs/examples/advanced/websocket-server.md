---
id: websocket-server
title: "WebSocket Server Example"
sidebar_label: "WebSocket Server"
---

```scala mdoc:silent
import zio._

import zio.http.ChannelEvent.{ChannelRead, ExceptionCaught, UserEvent, UserEventTriggered}
import zio.http._
import zio.http.socket._

object WebSocketAdvanced extends ZIOAppDefault {

  val httpSocket: Http[Any, Throwable, WebSocketChannel, Unit] =
    Http.collectZIO[WebSocketChannel] { case channel =>
      channel
        .receive
        .flatMap {
          case ChannelRead(WebSocketFrame.Text("end")) =>
            channel.shutdown

          // Send a "bar" if the server sends a "foo"
          case ChannelRead(WebSocketFrame.Text("foo")) =>
            channel.send(ChannelRead(WebSocketFrame.text("bar")))

          // Send a "foo" if the server sends a "bar"
          case ChannelRead(WebSocketFrame.Text("bar")) =>
            channel.send(ChannelRead(WebSocketFrame.text("foo")))

          // Echo the same message 10 times if it's not "foo" or "bar"
          case ChannelRead(WebSocketFrame.Text(text)) =>
            channel.send(ChannelRead(WebSocketFrame.text(text))).repeatN(10)

          // Send a "greeting" message to the server once the connection is established
          case UserEventTriggered(UserEvent.HandshakeComplete) =>
            channel.send(ChannelRead(WebSocketFrame.text("Greetings!")))

          // Log when the channel is getting closed
          case ChannelRead(WebSocketFrame.Close(status, reason)) =>
            Console.printLine("Closing channel with status: " + status + " and reason: " + reason)

          // Print the exception if it's not a normal close
          case ExceptionCaught(cause) =>
            Console.printLine(s"Channel error!: ${cause.getMessage}")

          case _ =>
            ZIO.unit
        }
        .forever
    }

  val socketApp: SocketApp[Any] =
    httpSocket.toSocketApp

  val app: Http[Any, Nothing, Request, Response] =
    Http.collectZIO[Request] {
      case Method.GET -> !! / "greet" / name  => ZIO.succeed(Response.text(s"Greetings ${name}!"))
      case Method.GET -> !! / "subscriptions" => socketApp.toResponse
    }

  override val run = Server.serve(app).provide(Server.default)
}
```