---
id: how-to-handle-WebSocket-exceptions-and-errors
title: "How to handle WebSocket exceptions and errors in ZIO"
---

## Introduction

This guide demonstrates how to implement a WebSocket client using the ZIO library with support for automatic reconnection in case of errors or exceptions.

## Prerequisites

Before you begin, ensure you have the following:

- Basic knowledge of Scala and ZIO library
- Scala development environment set up
- ZIO library added to your project dependencies

## Step-by-Step Guide

### 1. Define WebSocket URL

First, define the WebSocket URL to which you want to connect:

```scala
val url = "ws://ws.vi-server.org/mirror"
```

### 2. Implement WebSocket Client

Next, implement the WebSocket client using ZIO's `Handler.webSocket` function. This function listens for all WebSocket channel events and handles them accordingly.

```scala
def makeSocketApp(p: Promise[Nothing, Throwable]): SocketApp[Any] =
  Handler.webSocket { channel =>
    channel.receiveAll {
      case UserEventTriggered(UserEvent.HandshakeComplete) =>
        // On connect, send a "foo" message to start the echo loop
        channel.send(ChannelEvent.Read(WebSocketFrame.text("foo")))

      case Read(WebSocketFrame.Text("foo"))                =>
        // On receiving "foo", reply with another "foo" to keep echo loop going
        ZIO.logInfo("Received foo message.") *>
          ZIO.sleep(1.second) *>
          channel.send(ChannelEvent.Read(WebSocketFrame.text("foo")))

      case ExceptionCaught(t)                              =>
        // Handle exception and convert it to failure to signal the shutdown of the socket connection via the promise
        ZIO.fail(t)

      case _ =>
        ZIO.unit
    }
  }.tapErrorZIO { f =>
    // Signal failure to application
    p.succeed(f)
  }
```

### 3. Connect and Handle Errors

Connect to the WebSocket server and handle errors or exceptions. Use a promise to notify the application about WebSocket errors and trigger reconnection.

```scala
val app: ZIO[Any with Client with Scope, Throwable, Unit] = {
  (for {
    p <- zio.Promise.make[Nothing, Throwable]
    _ <- makeSocketApp(p).connect(url).catchAll { t =>
      // Convert a failed connection attempt to an error to trigger a reconnect
      p.succeed(t)
    }
    f <- p.await
    _ <- ZIO.logError(s"App failed: $f")
    _ <- ZIO.logError(s"Trying to reconnect...")
    _ <- ZIO.sleep(1.seconds)
  } yield {
    ()
  }) *> app
}
```

### 4. Run the Application

Finally, run the application by providing the required ZIO environments (`Client.default` and `Scope.default`).

```scala
val run =
  app.provide(Client.default, Scope.default)
```

## Conclusion

By following this guide, you have learned how to implement a WebSocket client in Scala using the ZIO library with support for handling exceptions and automatic reconnection. This approach ensures robustness and reliability in WebSocket communications.