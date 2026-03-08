---
id: real-time-chat-with-datastar
title: "Building a Real-time Chat with Datastar"
sidebar_label: "Real-time Chat"
---

This guide walks through building a real-time multi-client chat application using ZIO HTTP and Datastar. The application demonstrates several powerful patterns for building reactive web applications with server-driven UI updates.

## What We're Building

A fully functional chat application where:
- Multiple users can join and chat simultaneously
- Messages appear in real-time across all connected clients
- No page refreshes required - updates stream via Server-Sent Events (SSE)
- Clean, reactive UI with Datastar signal bindings

## Key Concepts Demonstrated

- **ZIO Hub** for broadcasting messages to multiple subscribers
- **Server-Sent Events (SSE)** for real-time updates via `events { handler {...} }`
- **Datastar signals** for reactive form bindings
- **Type-safe request handling** with `readSignals[T]`
- **HTML templating** with the `template2` DSL

## Prerequisites

Add the Datastar SDK dependency to your project:

```scala
libraryDependencies += "dev.zio" %% "zio-http-datastar-sdk" % "@VERSION@"
```

## Architecture Overview

The chat application consists of four components:

1. **ChatMessage** - Immutable message model with ZIO Schema
2. **ChatRoom** - In-memory state using `Ref` + message broadcasting via `Hub`
3. **MessageRequest** - Request model for signal binding
4. **ChatServer** - HTTP routes and HTML template

```
┌─────────────────┐     POST /chat/send      ┌─────────────────┐
│   Browser 1     │ ───────────────────────► │                 │
│   (Datastar)    │                          │   ChatServer    │
│                 │ ◄─────────────────────── │                 │
└─────────────────┘     SSE: new messages    │   ┌─────────┐   │
                                             │   │ ChatRoom│   │
┌─────────────────┐     POST /chat/send      │   │  (Hub)  │   │
│   Browser 2     │ ───────────────────────► │   └─────────┘   │
│   (Datastar)    │                          │                 │
│                 │ ◄─────────────────────── │                 │
└─────────────────┘     SSE: new messages    └─────────────────┘
```

## Implementation

### 1. Message Model

The `ChatMessage` case class represents a chat message with automatic ID and timestamp generation:

```scala mdoc:passthrough
import utils._

printSource("zio-http-example-datastar-chat/src/main/scala/example/datastar/chat/ChatMessage.scala")
```

Key points:
- Uses ZIO Schema for type-safe serialization
- Factory method generates UUID and timestamp automatically
- Scala 3 `given` syntax for Schema derivation

### 2. Request Model

The `MessageRequest` captures the form data sent when a user submits a message:

```scala mdoc:passthrough
printSource("zio-http-example-datastar-chat/src/main/scala/example/datastar/chat/MessageRequest.scala")
```

This model maps directly to the Datastar signals `$username` and `$message` defined in the HTML template.

### 3. Chat Room with Hub

The `ChatRoom` manages message state and broadcasts new messages to all connected clients:

```scala mdoc:passthrough
printSource("zio-http-example-datastar-chat/src/main/scala/example/datastar/chat/ChatRoom.scala")
```

Key patterns:
- **`Ref[List[ChatMessage]]`** - Thread-safe mutable reference for message history
- **`Hub[ChatMessage]`** - Broadcasts messages to all subscribers
- **`subscribe`** - Returns a `ZStream` that receives new messages
- **ZLayer** - Provides the `ChatRoom` as a dependency

### 4. Server and Routes

The `ChatServer` ties everything together with HTTP routes and the HTML template:

```scala mdoc:passthrough
printSource("zio-http-example-datastar-chat/src/main/scala/example/datastar/chat/ChatServer.scala")
```

Let's break down the key parts:

#### Signal Declarations

```scala
private val $username = Signal[String]("username")
private val $message  = Signal[String]("message")
```

These typed signal declarations are used in the HTML template for two-way data binding.

#### HTML Template with Datastar

The template uses several Datastar attributes:

- **`datastarScript`** - Includes the Datastar JavaScript library
- **`dataInit`** - Triggers initial data load via SSE when the page loads
- **`dataSignals($username) := ""`** - Declares reactive signals with initial values
- **`dataBind("username")`** - Two-way binds input value to signal
- **`dataOn.keydown := js"..."`** - Handles keyboard events
- **`dataOn.click := js"@post('/chat/send')"`** - Sends message on button click

#### SSE Streaming Route

```scala
Method.GET / "chat" / "messages" -> events {
  handler {
    for
      messages <- ChatRoom.getMessages
      _        <- ServerSentEventGenerator.patchElements(
                    messages.map(messageTemplate),
                    PatchElementOptions(
                      selector = Some(id("message-list")),
                      mode = ElementPatchMode.Inner,
                    ),
                  )
      messages <- ChatRoom.subscribe
      _        <- messages.mapZIO { message =>
                    ServerSentEventGenerator.patchElements(
                      messageTemplate(message),
                      PatchElementOptions(
                        selector = Some(id("message-list")),
                        mode = ElementPatchMode.Append,
                      ),
                    )
                  }.runDrain
    yield ()
  }
}
```

This route:
1. Sends existing messages immediately (with `Inner` mode to replace content)
2. Subscribes to the Hub for new messages
3. Streams each new message as an SSE event (with `Append` mode)

#### Message Sending Route

```scala
Method.POST / "chat" / "send" ->
  handler { (req: Request) =>
    for
      rq  <- req.readSignals[MessageRequest]
      msg  = ChatMessage(username = rq.username, content = rq.message)
      _   <- ChatRoom.addMessage(msg)
    yield Response.ok
  }
```

The `readSignals[T]` method extracts Datastar signals from the request body into a typed case class.

## Running the Example

Clone the ZIO HTTP repository and run the example:

```bash
git clone https://github.com/zio/zio-http.git
cd zio-http
sbt "zioHttpExampleDatastarChat/run"
```

Open your browser to [http://localhost:8080/chat](http://localhost:8080/chat).

To test multi-client functionality, open multiple browser tabs or windows.

## How It Works

1. **Page Load**: Browser requests `/chat`, receives HTML with embedded Datastar
2. **Initial Connection**: `dataInit` triggers GET `/chat/messages`, establishing SSE connection
3. **Existing Messages**: Server sends all existing messages via `patchElements` with `Inner` mode
4. **Subscription**: Server subscribes to Hub and keeps connection open
5. **User Types**: Input changes update `$username` and `$message` signals locally
6. **User Sends**: Button click or Enter key triggers POST `/chat/send` with signals
7. **Broadcast**: Server adds message to ChatRoom, Hub broadcasts to all subscribers
8. **Real-time Update**: Each subscriber's SSE connection receives new message, DOM updates

## Styling

The application uses an external CSS file loaded via `style.inlineResource("chat.css")`. This demonstrates how to load static resources in ZIO HTTP applications.

## Next Steps

This example can be extended with:
- **User authentication** - Add login flow before chat access
- **Multiple rooms** - Support different chat channels
- **Message persistence** - Store messages in a database
- **Typing indicators** - Show when users are typing
- **Read receipts** - Track message delivery status

## Related Documentation

- [Datastar SDK Reference](../reference/datastar-sdk/index.md) - Complete API documentation
- [Server-Sent Events](../examples/server-sent-events-in-endpoints.md) - SSE fundamentals
- [HTML Templating](../reference/body/template.md) - Template DSL reference
