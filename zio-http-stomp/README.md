# ZIO HTTP STOMP

STOMP (Simple Text Oriented Messaging Protocol) support for ZIO HTTP.

## Overview

This module provides support for STOMP protocol versions 1.0, 1.1, and 1.2 as defined by the [STOMP specification](https://stomp.github.io/stomp-specification-1.2.html).

## Features

- ✅ Full STOMP 1.2 support (recommended)
- ✅ STOMP 1.1 and 1.0 compatibility
- ✅ Type-safe frame encoding/decoding
- ✅ Stream-based API for continuous messaging
- ✅ Integration with ZIO HTTP Response and Body
- ✅ Endpoint API support for type-safe STOMP endpoints
- ✅ No external dependencies (pure ZIO implementation)
- ✅ Header escaping according to STOMP specification
- ✅ Transaction support
- ✅ Heart-beat support

## Installation

Add the following dependency to your `build.sbt`:

```scala
libraryDependencies += "dev.zio" %% "zio-http-stomp" % "<version>"
```

## Quick Start

### Creating STOMP Frames

```scala
import zio.http._
import zio.http.stomp._

// Connect frame
val connect = StompFrame.connect(
  host = "stomp.example.com",
  login = Some("user"),
  passcode = Some("password"),
  acceptVersion = "1.2"
)

// Send frame
val send = StompFrame.send(
  destination = "/queue/messages",
  body = "Hello, STOMP!",
  contentType = Some("text/plain"),
  additionalHeaders = Map.empty
)

// Subscribe frame
val subscribe = StompFrame.subscribe(
  destination = "/topic/news",
  id = "sub-0",
  ack = "client"
)

// Message frame
val message = StompFrame.message(
  destination = "/topic/news",
  messageId = "msg-123",
  subscription = "sub-0",
  body = Chunk.fromArray("Breaking news!".getBytes)
)
```

### Encoding and Decoding

```scala
import zio._

// Encode a frame to bytes
val bytes: Chunk[Byte] = frame.encode

// Decode bytes to a frame
val decoded: Either[String, StompFrame] = StompFrame.decode(bytes)
```

### Integration with Response

```scala
import zio.stream._

// Single frame response
val response1 = Response.fromStompFrame(
  StompFrame.connected(version = "1.2", session = Some("abc123"))
)

// Stream of frames
val frames: ZStream[Any, Nothing, StompFrame] = ZStream.fromIterable(
  Seq(
    StompFrame.message(...),
    StompFrame.message(...),
    StompFrame.message(...)
  )
)
val response2 = Response.fromStompFrames(frames)
```

### Decoding from Body

```scala
// Decode single frame
for {
  frame <- body.asStompFrame
} yield frame

// Decode stream of frames
val frames: ZStream[Any, Throwable, StompFrame] = body.asStompFrames
```

### Endpoint API Integration

```scala
import zio.http.endpoint._

// Endpoint that streams STOMP frames
val stompEndpoint = 
  Endpoint(Method.GET / "stomp")
    .outStream[StompFrame](MediaType.parseCustomMediaType("application/stomp").get)

val route = stompEndpoint.implementHandler {
  Handler.succeed(stompFrameStream)
}
```

## STOMP Protocol Support

### STOMP 1.2 (Default)
- Full specification support
- Header escaping: `\n`, `\r`, `:`, `\\`
- NACK command
- Heart-beat support
- Content-length mandatory for binary data

### STOMP 1.1
- Header escaping support
- NACK command
- Heart-beat support

### STOMP 1.0
- Basic specification support
- No header escaping
- No NACK command
- No heart-beat support

## Common Use Cases

### Client Connection Handshake

```scala
// Client sends CONNECT
val connect = StompFrame.connect(
  host = "stomp.example.com",
  acceptVersion = "1.2",
  heartBeat = Some((1000, 1000)) // 1 second intervals
)

// Server responds with CONNECTED
val connected = StompFrame.connected(
  version = "1.2",
  session = Some("session-abc123"),
  heartBeat = Some((1000, 1000))
)
```

### Subscribe and Receive Messages

```scala
// Subscribe to a destination
val subscribe = StompFrame.subscribe(
  destination = "/topic/news",
  id = "sub-0",
  ack = "client" // manual acknowledgment
)

// Receive message
val message = StompFrame.message(
  destination = "/topic/news",
  messageId = "msg-1",
  subscription = "sub-0",
  body = Chunk.fromArray("News content".getBytes)
)

// Acknowledge receipt
val ack = StompFrame.ack(id = "msg-1")
```

### Transactions

```scala
// Begin transaction
val begin = StompFrame.begin("tx-1")

// Send messages within transaction
val send1 = StompFrame.send(
  "/queue/a",
  "message 1",
  None,
  Map("transaction" -> "tx-1")
)
val send2 = StompFrame.send(
  "/queue/b",
  "message 2",
  None,
  Map("transaction" -> "tx-1")
)

// Commit or abort transaction
val commit = StompFrame.commit("tx-1")
val abort = StompFrame.abort("tx-1")
```

### Error Handling

```scala
val error = StompFrame.error(
  message = Some("Invalid destination"),
  body = Some("The specified destination does not exist"),
  receiptId = Some("receipt-123")
)
```

## Testing

The module includes comprehensive tests for:
- Frame encoding/decoding
- All STOMP commands
- Header escaping
- Stream processing
- Response/Body integration
- Endpoint API integration
- STOMP protocol scenarios

Run tests with:
```bash
sbt zioHttpStomp/test
```

## License

Apache License 2.0

