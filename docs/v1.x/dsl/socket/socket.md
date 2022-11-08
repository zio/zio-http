---
title: "Socket"
sidebar_label: "Socket"
---

Websocket support can be added to your Http application using the same `Http` domain, something like this —

```scala
import zio.http._
import zio.socket._

val socket = Http.collectZIO[WebSocketChannelEvent] {
  case ChannelEvent(ch, ChannelRead(WebSocketFrame.Text("foo"))) =>
    ch.writeAndFlush(WebSocketFrame.text("bar"))
}

val http = Http.collectZIO[Request] {
  case Method.GET -> !! / "subscriptions" => socket.toSocketApp.toResponse
}
```

The WebSocket API leverages the already powerful `Http` domain to write web socket apps. The difference is that instead
of collecting `Request` we collect `ChannelEvent` or more specifically `WebSocketChannelEvent`. And, instead of
returning
a `Response` we return `Unit`, because we use the channel (which is available in the event) to write content directly.

## Channel

Essentially whenever there is a connection created between a server and client a channel is created on both sides. The
channel is a low level api that allows us to send and receive arbitrary messages.

When we upgrade a Http connection to WebSocket, we create a specialized channel that only allows websocket frames to be
sent and received. The access to channel is available thru the `ChannelEvent` api.

## ChannelEvents

A `ChannelEvent` is an immutable, type-safe representation of an event that's happened on a channel and it looks like
this —

```scala
case class ChannelEvent[A, B](channel: Channel[A], event: Event[B])
```

It contains two elements — The **Channel** on which the event was triggered and the actual **Event** that was triggered.
The
type param `A` on the Channel represents the kind of message one can **write** using the channel and the type param `B`
represents the kind of messages that can be received on the channel.

The type `WebSocketChannelEvent` is a type alias to `ChannelEvent[WebsocketFrame, WebSocketFrame]`. Meaning a channel
that only accepts `WebSocketFrame` and produces `WebSocketFrame` type of messages.

## Using `Http`

We can use `Http.collect` to select the events that we care about for our use case, like in the above example we are
only interested in the `ChannelRead` event. There are other life cycle events such as `ChannelRegistered`
and `ChannelUnregistered` that one might want to hook onto for some other use cases.

The main benefit of using `Http` is that one can write custom middlewares that can process incoming and outgoing
messages easily, for eg:

```scala
val userAction = Http.collect[ChannelEvent[Action, Command]] {
  case CreateAccount(name, password) => ???
  case DeleteAccount(id) => ???
}

val codec: Middleware[Any, Nothing, ChannelEvent[Action, Command], Unit, WebSocketChannelEvent, Unit]

val socket = userAction @@ codec
```

## SocketApp

The `Http` that accepts `WebSocketChannelEvent` isn't enough to create a websocket connection. There some other settings
that one might need to configure in a websocket connection, things such as `handshakeTimeout` or `subProtocol` etc. For
those purposes a Http of the type `Http[R, E, WebSocketChannelEvent, Unit]` needs to converted into a `SocketApp` using
the `toSocketApp` method first, before it can be sent as a response. Consider the following example where we set a few
additional properties for the websocket connection.

```scala
socket
  .toSocketApp
  .withDecoder(SocketDecoder.skipUTF8Validation)
  .withEncoder(SocketProtocol.subProtocol("json") ++ SocketProtocol.handshakeTimeout(5 seconds))
  .toResponse
```