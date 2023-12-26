---
id: websocket
title: "WebSocket Example"
sidebar_label: "WebSocket Server & Client"
---

This example shows how to create a WebSocket server using ZIO Http and how to write a client to connect to it.

## Server

First we define a `WebSocketApp` that will handle the WebSocket connection. 
The `Handler.webSocket` constructor gives access to the `WebSocketChannel`. The channel can be used to receive messages from the client and send messages back.
We use the `receiveAll` method, to pattern match on the different channel events that could occur.
The most important events are `Read` and `UserEventTriggered`. The `Read` event is triggered when the client sends a message to the server. The `UserEventTriggered` event is triggered when the connection is established.
We can identify the successful connection of a client by receiving a `UserEventTriggered(UserEvent.HandshakeComplete)` event. And if the client sends us a text message, we will receive a `Read(WebSocketFrame.Text(<text>))` event.

Our WebSocketApp will handle the following events send by the client:
* If the client connects to the server, we will send a "Greetings!" message to the client.
* If the client sends "foo", we will send a "bar" message back to the client.
* If the client sends "bar", we will send a "foo" message back to the client.
* If the client sends "end", we will close the connection.
* If the client sends any other message, we will send the same message back to the client 10 times.

For the client to establish a connection with the server, we offer the `/subscriptions` endpoint.

```scala mdoc:silent

import zio._

import zio.http.ChannelEvent.{ExceptionCaught, Read, UserEvent, UserEventTriggered}
import zio.http._
import zio.http.codec.PathCodec.string

object WebSocketAdvanced extends ZIOAppDefault {

  val socketApp: WebSocketApp[Any] =
    Handler.webSocket { channel =>
      channel.receiveAll {
        case Read(WebSocketFrame.Text("end")) =>
          channel.shutdown

        // Send a "bar" if the client sends a "foo"
        case Read(WebSocketFrame.Text("foo")) =>
          channel.send(Read(WebSocketFrame.text("bar")))

        // Send a "foo" if the client sends a "bar"
        case Read(WebSocketFrame.Text("bar")) =>
          channel.send(Read(WebSocketFrame.text("foo")))

        // Echo the same message 10 times if it's not "foo" or "bar"
        case Read(WebSocketFrame.Text(text)) =>
          channel.send(Read(WebSocketFrame.text(text))).repeatN(10)

        // Send a "greeting" message to the client once the connection is established
        case UserEventTriggered(UserEvent.HandshakeComplete) =>
          channel.send(Read(WebSocketFrame.text("Greetings!")))

        // Log when the channel is getting closed
        case Read(WebSocketFrame.Close(status, reason)) =>
          Console.printLine("Closing channel with status: " + status + " and reason: " + reason)

        // Print the exception if it's not a normal close
        case ExceptionCaught(cause) =>
          Console.printLine(s"Channel error!: ${cause.getMessage}")

        case _ =>
          ZIO.unit
      }
    }

  val app: HttpApp[Any] =
    Routes(Method.GET / "subscriptions" -> handler(socketApp.toResponse)).toHttpApp

  override val run = Server.serve(app).provide(Server.default)
}
```

A few things worth noting:
 * `Server.default` starts a server on port 8080.
 * `socketApp.toResponse` converts the `WebSocketApp` to a `Response`, so we can serve it with `handler`.


## Client

The client will connect to the server and send a message to the server every time the user enters a message in the console.
For this we will use the `Console.readLine` method to read a line from the console. We will then send the message to the server using the `WebSocketChannel.send` method.
But since we don't want to reconnect to the server every time the user enters a message, we will use a `Queue` to store the messages. We will then use the `Queue.take` method to take a message from the queue and send it to the server, whenever a new message is available.
Adding a new message to the queue, as well as sending the messages to the server, should happen in a loop in the background. For this we will use the operators `forever` (looping) and `forkDaemon` (fork to a background fiber).

Again we will use the `Handler.webSocket` constructor to define how to handle messages and create a `WebSocketApp`. But this time, instead of serving the `WebSocketApp` we will use the `connect` method to establish a connection to the server.
All we need for that, is the URL of the server. In our case it's `"ws://localhost:8080/subscriptions"`.

```scala mdoc:silent
import zio._

import zio.http._

object WebSocketAdvancedClient extends ZIOAppDefault {

  def sendChatMessage(message: String): ZIO[Queue[String], Throwable, Unit] =
    ZIO.serviceWithZIO[Queue[String]](_.offer(message).unit)

  def processQueue(channel: WebSocketChannel): ZIO[Queue[String], Throwable, Unit] = {
    for {
      queue <- ZIO.service[Queue[String]]
      msg   <- queue.take
      _     <- channel.send(Read(WebSocketFrame.Text(msg)))
    } yield ()
  }.forever.forkDaemon.unit

  private def webSocketHandler: ZIO[Queue[String] with Client with Scope, Throwable, Response] =
    Handler.webSocket { channel =>
      for {
        _ <- processQueue(channel)
        _ <- channel.receiveAll {
          case Read(WebSocketFrame.Text(text)) =>
            Console.printLine(s"Server: $text")
          case _                               =>
            ZIO.unit
        }
      } yield ()
    }.connect("ws://localhost:8080/subscriptions")

  override val run =
    (for {
      _ <- webSocketHandler
      _ <- Console.readLine.flatMap(sendChatMessage).forever.forkDaemon
      _ <- ZIO.never
    } yield ())
      .provideSome[Scope](
        Client.default,
        ZLayer(Queue.bounded[String](100)),
      )

}
```
While we access here `Queue[String]` via the ZIO environment, you should use a service in a real world application, that requires a queue as one of its constructor dependencies.
See [ZIO Services](https://zio.dev/reference/service-pattern/) for more information.