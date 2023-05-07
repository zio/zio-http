---
id: websocket
title: WebSocket Example
sidebar_label: WebSocket
---

```scala mdoc:silent
import zio._

import zio.http.ChannelEvent.ChannelRead
import zio.http._

object WebSocketEcho extends ZIOAppDefault {
  private val socket: Http[Any, Throwable, WebSocketChannel, Unit] =
    Http.collectZIO[WebSocketChannel] { case channel =>
      channel
        .receive
        .flatMap {
          case ChannelRead(WebSocketFrame.Text("FOO")) =>
            channel.send(ChannelRead(WebSocketFrame.text("BAR")))

          case ChannelRead(WebSocketFrame.Text("BAR")) =>
            channel.send(ChannelRead(WebSocketFrame.text("FOO")))

          case ChannelRead(WebSocketFrame.Text(text)) =>
            channel.send(ChannelRead(WebSocketFrame.text(text))).repeatN(10)

          case _ =>
            ZIO.unit
        }
        .forever
    }

  private val app: Http[Any, Nothing, Request, Response] =
    Http.collectZIO[Request] {
      case Method.GET -> !! / "greet" / name  => ZIO.succeed(Response.text(s"Greetings {$name}!"))
      case Method.GET -> !! / "subscriptions" => socket.toSocketApp.toResponse
    }

  override val run = Server.serve(app).provide(Server.default)
}

```
