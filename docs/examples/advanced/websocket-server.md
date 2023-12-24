---
id: websocket-server
title: "WebSocket Server Example"
sidebar_label: "WebSocket Server"
---

```scala mdoc:silent
import zio._

import zio.http.ChannelEvent.{ExceptionCaught, Read, UserEvent, UserEventTriggered}
import zio.http._
import zio.http.codec.PathCodec.string

object WebSocketAdvanced extends ZIOAppDefault {

  val socketApp: WebSocketApp[Any] =
    Handler.webSocket { channel =>
      channel.receiveAll {
        case Read(WebSocketFrame.Text("end"))                =>
          channel.shutdown

        // Send a "bar" if the server sends a "foo"
        case Read(WebSocketFrame.Text("foo"))                =>
          channel.send(Read(WebSocketFrame.text("bar")))

        // Send a "foo" if the server sends a "bar"
        case Read(WebSocketFrame.Text("bar"))                =>
          channel.send(Read(WebSocketFrame.text("foo")))

        // Echo the same message 10 times if it's not "foo" or "bar"
        case Read(WebSocketFrame.Text(text))                 =>
          channel.send(Read(WebSocketFrame.text(text))).repeatN(10)

        // Send a "greeting" message to the server once the connection is established
        case UserEventTriggered(UserEvent.HandshakeComplete) =>
          channel.send(Read(WebSocketFrame.text("Greetings!")))

        // Log when the channel is getting closed
        case Read(WebSocketFrame.Close(status, reason))      =>
          Console.printLine("Closing channel with status: " + status + " and reason: " + reason)

        // Print the exception if it's not a normal close
        case ExceptionCaught(cause)                          =>
          Console.printLine(s"Channel error!: ${cause.getMessage}")

        case _ =>
          ZIO.unit
      }
    }

  val app: HttpApp[Any] =
    Routes(
      Method.GET / "greet" / string("name") -> handler { (name: String, req: Request) =>
        Response.text(s"Greetings ${name}!")
      },
      Method.GET / "subscriptions"          -> handler(socketApp.toResponse),
    ).toHttpApp

  override val run = Server.serve(app).provide(Server.default)
}
```