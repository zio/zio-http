---
id: test-channel
title: "TestChannel"
---

`TestChannel` is an in-memory bidirectional message channel for testing WebSocket handlers. It simulates a WebSocket connection between client and server, allowing handlers to exchange messages without real network I/O. All communication happens in-memory and synchronously, enabling fast, deterministic WebSocket tests.

```scala
case class TestChannel(
  in: Queue[WebSocketChannelEvent],
  out: Queue[WebSocketChannelEvent],
  promise: Promise[Nothing, Unit],
) extends WebSocketChannel {
  def receive: Task[WebSocketChannelEvent]
  def receiveAll[Env, Err](f: WebSocketChannelEvent => ZIO[Env, Err, Any]): ZIO[Env, Err, Unit]
  def send(event: WebSocketChannelEvent): Task[Unit]
  def sendAll(events: Iterable[WebSocketChannelEvent]): Task[Unit]
  def shutdown: UIO[Unit]
  def awaitShutdown: UIO[Unit]
}
```

Key properties:
- **Bidirectional** — Both client and server can send and receive messages independently
- **In-Memory Queues** — Uses bounded queues for message buffering
- **Frame Types** — Supports all WebSocket frame types: text, binary, control frames
- **Lifecycle Management** — Handles connection handshakes and graceful shutdown
- **Automatic Coordination** — Two TestChannels coordinate via shared queues and promises

### Role in Module

`TestChannel` is the **primary type for WebSocket testing** in zio-http-testkit. It provides a simulated WebSocket connection that handlers can interact with directly.

**Typically used with:** WebSocketApp (server endpoint), WebSocketHandler (application logic), TestServer or TestClient (to serve/access the WebSocket)

**Complementary types:**
- TestServer — For testing WebSocket endpoints you serve
- TestClient — For testing WebSocket clients you implement
- HttpTestAspect — For testing mode-dependent WebSocket behavior

## Motivation

WebSocket testing requires special handling compared to HTTP testing:

1. **Bidirectional communication** — Both client and server initiate messages (not request-response)
2. **Long-lived connections** — Connections persist across multiple message exchanges
3. **Stateful handlers** — Handlers maintain state within a connection session
4. **Complex message patterns** — Echo, broadcast, publish-subscribe, request-reply patterns

Real WebSocket testing with actual network connections is:
- **Slow** — Network I/O adds latency and test duration
- **Unreliable** — Network conditions, timeouts make tests flaky
- **Hard to test edge cases** — Difficult to simulate specific message sequences or errors
- **Difficult to coordinate** — Hard to verify exact message order and content

`TestChannel` solves this by providing an in-memory, synchronous channel that executes instantly without network latency, is fully deterministic and controllable, and lets you verify exact message sequences.

Use `TestChannel` when testing:
- WebSocket echo handlers
- Publish-subscribe message brokers
- Real-time notification systems
- Bidirectional request-reply patterns
- Connection lifecycle (handshake, close, error handling)

## Quick Showcase

Here's a complete example: create a WebSocket echo handler, test it with TestChannel:

```scala mdoc:compile-only
import zio._
import zio.http._
import zio.http.ChannelEvent._

val test = for {
  // Create input and output queues
  inQueue  <- Queue.unbounded[WebSocketChannelEvent]
  outQueue <- Queue.unbounded[WebSocketChannelEvent]
  promise  <- Promise.make[Nothing, Unit]
  
  // Create two channels: one for client, one for server
  clientChannel <- TestChannel.make(inQueue, outQueue, promise)
  serverChannel <- TestChannel.make(outQueue, inQueue, promise)
  
  // Echo handler: receives message, sends it back
  echoHandler = Handler.webSocket { channel: WebSocketChannel =>
    channel.receiveAll {
      case Read(WebSocketFrame.Text(msg)) =>
        channel.send(Read(WebSocketFrame.text(msg)))
      case _ => ZIO.unit
    }
  }
  
  // Server side: run echo handler on server channel (simplified for documentation)
  // In real tests, this would handle full request/response
  
  // Client side: send and receive messages
  // _ <- clientChannel.send(Read(WebSocketFrame.text("Hello")))
  // response <- clientChannel.receive
} yield ()
```

## Construction / Creating TestChannel

TestChannel instances are created via the factory method:

### `TestChannel.make` — Create Connected Channel Pair

```scala
def make(
  in: Queue[WebSocketChannelEvent],
  out: Queue[WebSocketChannelEvent],
  promise: Promise[Nothing, Unit],
): ZIO[Any, Nothing, TestChannel]
```

Create a TestChannel from queues and a promise. Useful for manual setup or advanced scenarios.

```scala mdoc:compile-only
import zio._
import zio.http._
import zio.http.ChannelEvent._

val test = for {
  // Create queues for bidirectional communication
  queue1 <- Queue.unbounded[WebSocketChannelEvent]
  queue2 <- Queue.unbounded[WebSocketChannelEvent]
  
  // Create promise for shutdown coordination
  shutdownPromise <- Promise.make[Nothing, Unit]
  
  // Create two TestChannels that are connected
  // Channel1 sends to queue2, receives from queue1
  channel1 <- TestChannel.make(queue1, queue2, shutdownPromise)
  // Channel2 sends to queue1, receives from queue2
  channel2 <- TestChannel.make(queue2, queue1, shutdownPromise)
} yield (channel1, channel2)
```

Key behavior:
- Two TestChannels share queues but with swapped input/output (in-out are reversed)
- `promise` coordinates shutdown between both channels
- Sending to `out` queue makes data available for the other channel's `WebSocketChannel#receive`

### Manual Channel Creation Pattern

For most testing, you don't create TestChannel directly. Instead:
- **TestServer** — WebSocket endpoints automatically create TestChannels for incoming connections
- **TestClient** — WebSocket clients automatically create TestChannels when connecting
- **Handler.webSocket** — Your handler receives a TestChannel automatically

## Core Operations

### Message Exchange Group

TestChannel supports bidirectional message transmission:

#### `WebSocketChannel#send` — Send Single Message to Channel

```scala
trait WebSocketChannel {
  def send(event: WebSocketChannelEvent)(implicit trace: Trace): Task[Unit]
}
```

Send a single WebSocket event (message or control frame) to the channel. Available for both client and server side to send.

```scala mdoc:compile-only
import zio._
import zio.http._
import zio.http.ChannelEvent._

val test = for {
  inQueue  <- Queue.unbounded[WebSocketChannelEvent]
  outQueue <- Queue.unbounded[WebSocketChannelEvent]
  promise  <- Promise.make[Nothing, Unit]
  
  channel <- TestChannel.make(inQueue, outQueue, promise)
  
  // Send a text message
  _ <- channel.send(Read(WebSocketFrame.text("Hello")))
  
  // Send a binary message
  _ <- channel.send(Read(WebSocketFrame.binary(Chunk.fromArray("binary".getBytes))))
} yield ()
```

Key behavior:
- Non-blocking; offers message to output queue
- Fails if queue is full (bounded queue overflow)
- Performance: O(1) per send

#### `WebSocketChannel#sendAll` — Send Multiple Messages

```scala
trait WebSocketChannel {
  def sendAll(events: Iterable[WebSocketChannelEvent])(implicit trace: Trace): Task[Unit]
}
```

Send multiple WebSocket events atomically. Useful for bulk message injection.

```scala mdoc:compile-only
import zio._
import zio.http._
import zio.http.ChannelEvent._

val test = for {
  inQueue  <- Queue.unbounded[WebSocketChannelEvent]
  outQueue <- Queue.unbounded[WebSocketChannelEvent]
  promise  <- Promise.make[Nothing, Unit]
  
  channel <- TestChannel.make(inQueue, outQueue, promise)
  
  // Send multiple messages
  messages = List(
    Read(WebSocketFrame.text("Message 1")),
    Read(WebSocketFrame.text("Message 2")),
    Read(WebSocketFrame.text("Message 3"))
  )
  _ <- channel.sendAll(messages)
} yield ()
```

Key behavior:
- All messages sent in order
- Atomic operation; either all succeed or all fail

#### `WebSocketChannel#receive` — Receive Single Message

```scala
trait WebSocketChannel {
  def receive(implicit trace: Trace): Task[WebSocketChannelEvent]
}
```

Receive one WebSocket event from the channel. Blocks until an event is available.

```scala mdoc:compile-only
import zio._
import zio.http._
import zio.http.ChannelEvent._

val test = for {
  inQueue  <- Queue.unbounded[WebSocketChannelEvent]
  outQueue <- Queue.unbounded[WebSocketChannelEvent]
  promise  <- Promise.make[Nothing, Unit]
  
  channel1 <- TestChannel.make(inQueue, outQueue, promise)
  channel2 <- TestChannel.make(outQueue, inQueue, promise)
  
  // One channel sends
  _ <- channel1.send(Read(WebSocketFrame.text("Hello")))
  
  // Other channel receives
  event <- channel2.receive
} yield event

// event is Read(WebSocketFrame.Text("Hello"))
```

Key behavior:
- Blocks waiting for input queue
- Returns any event type: frames, control messages, errors
- Performance: O(1) per receive

#### `WebSocketChannel#receiveAll` — Receive and Process All Messages

```scala
trait WebSocketChannel {
  def receiveAll[Env, Err](
    f: WebSocketChannelEvent => ZIO[Env, Err, Any]
  )(implicit trace: Trace): ZIO[Env, Err, Unit]
}
```

Loop receiving messages and apply a function to each one. Continues until channel shuts down (receives `Unregistered` event). Core pattern for WebSocket handlers.

```scala mdoc:compile-only
import zio._
import zio.http._
import zio.http.ChannelEvent._

val test = for {
  inQueue  <- Queue.unbounded[WebSocketChannelEvent]
  outQueue <- Queue.unbounded[WebSocketChannelEvent]
  promise  <- Promise.make[Nothing, Unit]
  
  channel1 <- TestChannel.make(inQueue, outQueue, promise)
  channel2 <- TestChannel.make(outQueue, inQueue, promise)
  
  // Echo handler: receive and send back
  echoFiber <- channel1.receiveAll {
    case Read(WebSocketFrame.Text(msg)) =>
      channel1.send(Read(WebSocketFrame.text(msg)))
    case _ => ZIO.unit
  }.fork
  
  // Send messages from other side
  _ <- channel2.send(Read(WebSocketFrame.text("Ping")))
  
  // Receive echo
  echo <- channel2.receive
} yield echo

// echo is Read(WebSocketFrame.Text("Ping"))
```

Key behavior:
- Loop continues until `ChannelEvent.Unregistered` is received
- Processes each event with the handler function
- Handles both data frames and control frames
- Uses `ZIO.yieldNow` for fair scheduling

### Lifecycle Management

#### `WebSocketChannel#shutdown` — Gracefully Close Channel

```scala
trait WebSocketChannel {
  def shutdown(implicit trace: Trace): UIO[Unit]
}
```

Send shutdown signals to both input and output, clean up resources. Causes `WebSocketChannel#receiveAll` loops to exit.

```scala mdoc:compile-only
import zio._
import zio.http._
import zio.http.ChannelEvent._

val test = for {
  inQueue  <- Queue.unbounded[WebSocketChannelEvent]
  outQueue <- Queue.unbounded[WebSocketChannelEvent]
  promise  <- Promise.make[Nothing, Unit]
  
  channel <- TestChannel.make(inQueue, outQueue, promise)
  
  // Process messages
  loopFiber <- channel.receiveAll { _ => ZIO.unit }.fork
  
  // Later, shutdown the channel
  _ <- channel.shutdown
  
  // receiveAll loop exits due to Unregistered
  _ <- loopFiber.join
} yield ()
```

Key behavior:
- Sends `Unregistered` to both queues
- Completes the shutdown promise
- Allows all operations (send/receive) to finish
- Always succeeds

#### `WebSocketChannel#awaitShutdown` — Wait for Shutdown

```scala
trait WebSocketChannel {
  def awaitShutdown(implicit trace: Trace): UIO[Unit]
}
```

Block until the channel receives shutdown signal from the other side.

```scala mdoc:compile-only
import zio._
import zio.http._
import zio.http.ChannelEvent._

val test = for {
  inQueue  <- Queue.unbounded[WebSocketChannelEvent]
  outQueue <- Queue.unbounded[WebSocketChannelEvent]
  promise  <- Promise.make[Nothing, Unit]
  
  channel1 <- TestChannel.make(inQueue, outQueue, promise)
  channel2 <- TestChannel.make(outQueue, inQueue, promise)
  
  // One side waits for shutdown
  waitFiber <- channel1.awaitShutdown.fork
  
  // Other side initiates shutdown
  _ <- ZIO.sleep(10.millis)
  _ <- channel2.shutdown
  
  // Wait completes
  _ <- waitFiber.join
} yield ()
```

Key behavior:
- Non-blocking on shutdown signal
- Waits on shared promise
- Allows coordinated shutdown between both sides

## Common Patterns

### Echo Handler

Test a handler that echoes back all text messages:

```scala mdoc:compile-only
import zio._
import zio.http._
import zio.http.ChannelEvent._

val test = for {
  inQueue  <- Queue.unbounded[WebSocketChannelEvent]
  outQueue <- Queue.unbounded[WebSocketChannelEvent]
  promise  <- Promise.make[Nothing, Unit]
  
  serverCh <- TestChannel.make(inQueue, outQueue, promise)
  clientCh <- TestChannel.make(outQueue, inQueue, promise)
  
  // Echo handler
  echoFiber <- serverCh.receiveAll {
    case Read(WebSocketFrame.Text(msg)) =>
      serverCh.send(Read(WebSocketFrame.text(msg)))
    case _ => ZIO.unit
  }.fork
  
  // Test: send and receive
  _ <- clientCh.send(Read(WebSocketFrame.text("Hello")))
  response <- clientCh.receive
  
  _ <- serverCh.shutdown
  _ <- clientCh.shutdown
  _ <- echoFiber.join
} yield response
```

### Stateful Handler

Test a handler that maintains state (e.g., counter):

```scala mdoc:compile-only
import zio._
import zio.http._
import zio.http.ChannelEvent._

val test = for {
  inQueue  <- Queue.unbounded[WebSocketChannelEvent]
  outQueue <- Queue.unbounded[WebSocketChannelEvent]
  promise  <- Promise.make[Nothing, Unit]
  
  serverCh <- TestChannel.make(inQueue, outQueue, promise)
  clientCh <- TestChannel.make(outQueue, inQueue, promise)
  
  // Handler with mutable state
  counter <- Ref.make(0)
  handlerFiber <- serverCh.receiveAll { event =>
    counter.updateAndGet(_ + 1).flatMap { count =>
      serverCh.send(Read(WebSocketFrame.text(s"Count: $count")))
    }
  }.fork
  
  // Send messages and check responses
  _ <- clientCh.send(Read(WebSocketFrame.text("Message 1")))
  resp1 <- clientCh.receive
  
  _ <- clientCh.send(Read(WebSocketFrame.text("Message 2")))
  resp2 <- clientCh.receive
  
  _ <- serverCh.shutdown
  _ <- clientCh.shutdown
  _ <- handlerFiber.join
} yield (resp1, resp2)
```

### Broadcast Pattern

Test a handler that broadcasts messages between two clients:

```scala mdoc:compile-only
import zio._
import zio.http._
import zio.http.ChannelEvent._

val test = for {
  // Create queues for two clients
  in1  <- Queue.unbounded[WebSocketChannelEvent]
  out1 <- Queue.unbounded[WebSocketChannelEvent]
  in2  <- Queue.unbounded[WebSocketChannelEvent]
  out2 <- Queue.unbounded[WebSocketChannelEvent]
  promise <- Promise.make[Nothing, Unit]
  
  // Server channels for each client
  server1 <- TestChannel.make(in1, out1, promise)
  server2 <- TestChannel.make(in2, out2, promise)
  client1 <- TestChannel.make(out1, in1, promise)
  client2 <- TestChannel.make(out2, in2, promise)
  
  // Broadcast handler: forward messages between servers
  broadcastFiber <- ZIO.forkAll(List(
    server1.receiveAll { case Read(WebSocketFrame.Text(msg)) =>
      server2.send(Read(WebSocketFrame.text(s"From 1: $msg")))
    },
    server2.receiveAll { case Read(WebSocketFrame.Text(msg)) =>
      server1.send(Read(WebSocketFrame.text(s"From 2: $msg")))
    }
  ))
  
  // Test: messages flow between clients
  _ <- client1.send(Read(WebSocketFrame.text("Hello")))
  msg2 <- client2.receive
} yield msg2
```

## Integration with Other Types

### Within Module

**`TestServer`** — WebSocket endpoints in TestServer use TestChannel automatically when clients connect. When you add a WebSocket handler to TestServer and a client connects, TestServer creates TestChannels for bidirectional communication:

```scala mdoc:compile-only
import zio._
import zio.http._
import zio.http.ChannelEvent._

// Example WebSocket handler
val echoHandler = Handler.webSocket { channel: WebSocketChannel =>
  channel.receiveAll {
    case Read(WebSocketFrame.Text(msg)) =>
      channel.send(Read(WebSocketFrame.text(msg)))
    case _ => ZIO.unit
  }
}

// This handler would be added to TestServer
// TestServer creates TestChannels automatically for each client connection
```

**`TestClient`** — WebSocket clients created by TestClient use TestChannel:

```scala mdoc:compile-only
import zio._
import zio.http._
import zio.http.ChannelEvent._

val test = for {
  client <- ZIO.service[Client]
  
  // Install WebSocket server in TestClient
  _ <- TestClient.installSocketApp {
    Handler.webSocket { channel =>
      channel.receiveAll {
        case Read(WebSocketFrame.Text(msg)) =>
          channel.send(Read(WebSocketFrame.text(msg)))
        case _ => ZIO.unit
      }
    }
  }
  
  // Your code connects to WebSocket, gets TestChannel automatically
} yield ()
```

**`HttpTestAspect`** — Apply mode-dependent behavior testing to WebSocket handlers.

### External Modules

- **zio-http core** — Uses `WebSocketChannel`, `WebSocketFrame`, `WebSocketChannelEvent` types
- **zio** — Uses `ZIO`, `Queue`, `Promise`, `Ref` for effect management and concurrency
- **zio-http netty** — Netty driver provides real WebSocket support; TestChannel substitutes in tests

## API Reference

### Public Methods

| Method | Signature | Purpose |
|--------|-----------|---------|
| `WebSocketChannel#send` | `WebSocketChannelEvent => Task[Unit]` | Send single event to channel |
| `WebSocketChannel#sendAll` | `Iterable[WebSocketChannelEvent] => Task[Unit]` | Send multiple events |
| `WebSocketChannel#receive` | `Task[WebSocketChannelEvent]` | Receive single event |
| `WebSocketChannel#receiveAll` | `(WebSocketChannelEvent => ZIO[Env, Err, Any]) => ZIO[Env, Err, Unit]` | Loop receive and process |
| `WebSocketChannel#shutdown` | `UIO[Unit]` | Gracefully close channel |
| `WebSocketChannel#awaitShutdown` | `UIO[Unit]` | Wait for shutdown signal |

### Companion Object

| Method | Signature | Purpose |
|--------|-----------|---------|
| `TestChannel.make` | `(Queue, Queue, Promise) => ZIO[Any, Nothing, TestChannel]` | Create TestChannel from queues |

### WebSocketChannelEvent Types

| Event | Description |
|-------|-------------|
| `Read(frame)` | Incoming WebSocket frame (text, binary, etc.) |
| `Unregistered` | Channel shutdown signal |
| `ExceptionCaught(error)` | Error occurred in channel |
| `UserEventTriggered(event)` | Custom user events |

## See Also

- [TestServer](./test-server.md) — Testing WebSocket endpoints
- [TestClient](./test-client.md) — Testing WebSocket clients
- [HttpTestAspect](./http-test-aspect.md) — Testing mode-dependent behavior
