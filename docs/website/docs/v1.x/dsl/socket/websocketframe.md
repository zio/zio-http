---
title: "WebSocketFrame"
sidebar_label: "WebSocketFrame"
---
In the [WebSocket](https://datatracker.ietf.org/doc/html/rfc6455) protocol, communication happens using frames. ZIO
HTTP's [WebSocketFrame](https://github.com/zio/zio-http/blob/main/zio-http/src/main/scala/zio/socket/WebSocketFrame.scala)
is the representation of those frames. The domain defines the following type of frames:

* Text
* Binary
* Continuation
* Close
* Ping
* Pong

### Text

To create a Text frame that models textual data in the WebSocket protocol, you can use the `text` constructor.

```scala
val text = WebSocketFrame.text("Hello from ZIO-HTTP")
```

### Binary

To create a Binary frame that models raw binary data, you can use the `binary` constructor.

```scala
import zio.Chunk

val binary = WebSocketFrame.binary(Chunk.fromArray("Hello from ZIO-HTTP".getBytes(StandardCharsets.UTF_16)))
```

### Continuation

To create a Continuation frame to model a continuation fragment of the previous message, you can use the `continuation`
constructor.

```scala
import io.netty.buffer.Unpooled.copiedBuffer
import io.netty.util.CharsetUtil.UTF_16

val continuation = WebSocketFrame.continuation(copiedBuffer("Hello from ZIO-HTTP", UTF_16))
```

### Close

To create a Close frame for a situation where the connection needs to be closed, you can use the `close` constructor.
The constructor requires two arguments:

* Status
* Optional reason.

#### Constructing Close with just status

```scala
val close = WebSocketFrame.close(1000)
```

#### Constructing Close with status and a reason

```scala
val close = WebSocket.close(1000, "Normal Closure")
```

More information on status codes can be found
in [Section 7.4](https://datatracker.ietf.org/doc/html/rfc6455#section-7.4) of IETF's Data Tracker.

### Ping

Ping models heartbeat in the WebSocket protocol. The server or the client can at any time, after a successful handshake,
send a ping frame.

```scala
val ping = WebSocketFrame.ping
```

### Pong

Pong models the second half of the heartbeat in the WebSocket protocol. Upon receiving [ping](#ping), a pong needs to be
sent back.

```scala
val ping = WebSocketFrame.ping
```

## Pattern Matching on WebSocketFrame

ZIO HTTP envisions the WebSocketFrame as a [Sum](https://en.wikipedia.org/wiki/Tagged_union) type, which allows
exhaustive pattern matching to be performed on it.

You can do pattern matching on the WebSocketFrame type in the following way:

```scala
val frame: WebSocketFrame = ...

frame match {
  case WebSocketFrame.Binary(bytes) => ???
  case WebSocketFrame.Text(text) => ???
  case WebSocketFrame.Close(status, reason) => ???
  case WebSocketFrame.Ping => ???
  case WebSocketFrame.Pong => ???
  case WebSocketFrame.Continuation(buffer) => ???
}
```
