---
id: websocket
title: WebSocket Example
sidebar_label: WebSocket
---

```scala mdoc:silent
import zio._

import zio.http.ChannelEvent.Read
import zio.http._

object WebSocketEcho extends ZIOAppDefault {
  private val socketApp: SocketApp[Any] =
    Handler.webSocket { channel =>
      channel.receiveAll {
        case Read(WebSocketFrame.Text("FOO")) =>
          channel.send(Read(WebSocketFrame.text("BAR")))

        case Read(WebSocketFrame.Text("BAR")) =>
          channel.send(Read(WebSocketFrame.text("FOO")))

        case Read(WebSocketFrame.Text(text)) =>
          channel.send(Read(WebSocketFrame.text(text))).repeatN(10)

        case _ =>
          ZIO.unit
      }
    }

  private val app: Http[Any, Nothing, Request, Response] =
    Http.collectZIO[Request] {
      case Method.GET -> Root / "greet" / name  => ZIO.succeed(Response.text(s"Greetings {$name}!"))
      case Method.GET -> Root / "subscriptions" => socketApp.toResponse
    }

  override val run = Server.serve(app).provide(Server.default)
}

```
