---
id: socket
title: "Socket"
---

Websocket support can be added to your Http application using the same `Http` domain, something like this —

```scala mdoc:silent
import zio.http._
import zio._

val socket = Http.collectZIO[WebSocketChannel] { case channel =>
  channel
    .receive
    .flatMap {
      case ChannelEvent.Read(WebSocketFrame.Text("foo")) =>
        channel.send(ChannelEvent.Read(WebSocketFrame.text("bar")))
      case _ =>
        ZIO.unit
    }
    .forever
}

val http = Http.collectZIO[Request] {
  case Method.GET -> !! / "subscriptions" => socket.toSocketApp.toResponse
}
```

The WebSocket API leverages the already powerful `Http` domain to write web socket apps. The difference is that instead
of collecting `Request` we collect `Channel` or more specifically `WebSocketChannel`. And, instead of
returning
a `Response` we return `Unit`, because we use the channel to write content directly.

## Channel

Essentially, whenever there is a connection created between a server and client a channel is created on both sides. The
channel is a low level api that allows us to send and receive arbitrary messages.

When we upgrade a Http connection to WebSocket, we create a specialized channel that only allows websocket frames to be
sent and received. The access to channel is available through the `Channel` api.

## ChannelEvents

A `ChannelEvent` is an immutable, type-safe representation of an event that's happened on a channel, and it looks like
this:

```scala
sealed trait ChannelEvent[A]
```

It is the **Event** that was triggered. The type param `A` on the ChannelEvent represents the kind of message the event contains.

The type `WebSocketChannelEvent` is a type alias to `ChannelEvent[WebsocketFrame]`. Meaning an event that contains `WebSocketFrame` typed messages.

## Using `Http`

We can use `Http.collect` to select the events that we care about for our use case, like in the above example we are
only interested in the `ChannelRead` event. There are other life cycle events such as `ChannelRegistered`
and `ChannelUnregistered` that one might want to hook onto for some other use cases.
