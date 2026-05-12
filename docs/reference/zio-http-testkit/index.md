---
id: index
title: "ZIO HTTP Testkit"
---

import Tabs from '@theme/Tabs';
import TabItem from '@theme/TabItem';

`zio-http-testkit` provides testing infrastructure for ZIO HTTP applications, enabling developers to test HTTP logic without real servers, mock external dependencies, and validate WebSocket communication. Core types: `TestServer`, `TestClient`, `TestChannel`, `HttpTestAspect`.

Here's what you can do with the testkit:

```scala
// Test a server route directly
val routes = Routes(Method.GET / "users" -> Handler.text("Alice"))

// Test a client that calls external services
val testMockClient = for {
  _ <- TestClient.addRoute(Method.GET / "api" -> handler(Response.text("mock")))
  client <- ZIO.service[Client]
  response <- client(Request.get(URL.root / "api"))
} yield assertTrue(response.status == Status.Ok)

// Test WebSocket bidirectional messaging
val echoServer: WebSocketApp[Any] = Handler.webSocket { channel =>
  channel.receiveAll {
    case Read(WebSocketFrame.Text(msg)) =>
      channel.send(Read(WebSocketFrame.text(s"Echo: $msg")))
    case _ => ZIO.unit
  }
}
```

## Motivation

Testing HTTP applications is fundamentally different from testing pure functions. Traditional approaches are painful:

- **Real servers in tests** — Slow (seconds per test), hard to debug (network I/O), difficult to test edge cases (how do you simulate a timeout?).
- **Mocking HTTP libraries** — You're not really testing HTTP logic, just that you call the mock correctly. If the mock diverges from real behavior, tests pass but production fails.
- **Tests depending on external services** — Slow, flaky (services go down), hard to run locally, can't easily test error paths.

`zio-http-testkit` solves this by treating routes and WebSocket handlers as pure functions. You test them directly without any server infrastructure, making tests fast, deterministic, and realistic.

## Installation

Add this dependency to your `build.sbt`:

```scala
libraryDependencies += "dev.zio" %% "zio-http-testkit" % "@VERSION@" % Test
```

Scala versions: 2.13.x and 3.x

## Overview

The testkit provides four core types working together to cover the full testing spectrum:

- **`TestServer`** — Simulates an HTTP server. Configure routes, make HTTP requests via a client, verify responses. Use for integration testing multiple routes together.

- **`TestClient`** — Simulates an HTTP client. Mock external service responses, verify your code makes correct HTTP calls. Use when your application depends on external APIs.

- **`TestChannel`** — In-memory bidirectional message channel. Test WebSocket handlers with realistic message exchange patterns, no actual network.

- **`HttpTestAspect`** — Test utility for configuring HTTP runtime modes (Dev, Prod, Preprod). Use when your handler behavior varies by deployment mode.

## How They Work Together

The four core types work together in layers, each appropriate for different testing scenarios:

### Layer 1: Unit Testing with Direct Route Testing (90% of tests)

When you want the fastest feedback, invoke a `Handler` as a pure function:
```
Request ──> Route.runZIO ──> Response
```

No server infrastructure. Each test runs in isolation, testing business logic, routing patterns, and data transformations at lightning speed. Perfect for the majority of your test suite.

Example:

```scala mdoc:passthrough
import utils._

printSource("zio-http-example-testing/src/test/scala/example/testing/UnitTestingIndividualHandlers.scala")
```

### Layer 2: Integration Testing with External Mocking via TestClient (5% of tests)

When your handler calls external services, `TestClient` intercepts those calls:
```
Your Code ──> TestClient ──> Mocked Response
             (configured)
```

Your code uses the standard `Client` interface, unaware it's talking to a test mock. You control every response—success, failure, timeouts, anything. Test payment processors, third-party APIs, and resilience to failure without real network calls.

Example:

```scala mdoc:passthrough
import utils._

printSource("zio-http-example-testing/src/test/scala/example/testing/MockingExternalDependencies.scala")
```

### Layer 3: Full Integration Testing with TestServer (5% of tests)

When you need the complete picture—multiple `Route`s working together, state persisting across requests—bring in `TestServer`:
```
HTTP Request ──> TestServer ──> Route Matching ──> Handler ──> Response
                (localhost / in-process)
```

This is where you test feature workflows. User registration followed by email verification. API calls in sequence. Routes interacting as they would in production. It's the slowest approach, but it catches integration issues that simpler tests miss.

**Examples:**

Multiple routes working together:
```scala mdoc:passthrough
import utils._

printSource("zio-http-example-testing/src/test/scala/example/testing/IntegrationTestingMultipleRoutes.scala")
```

State persisting across requests:
```scala mdoc:passthrough
import utils._

printSource("zio-http-example-testing/src/test/scala/example/testing/TestingStateAcrossRequests.scala")
```

Testing error paths:
```scala mdoc:passthrough
import utils._

printSource("zio-http-example-testing/src/test/scala/example/testing/ErrorHandling.scala")
```

([source](https://github.com/zio/zio-http/blob/main/zio-http-example-testing/src/test/scala/example/testing/ErrorHandling.scala))

### Layer 4: WebSocket Testing with TestChannel (Special case)

For bidirectional communication, `TestChannel` lets both client and server exchange messages through in-memory queues:
```
Client Handler ──[TestChannel]──> Server Handler
(sends messages)                    (sends responses)
```

Test real-time notifications, chat messages, live data streams—all without actual network I/O. Both sides run concurrently, fully controllable.

Practical example:

```scala mdoc:passthrough
import utils._

printSource("zio-http-example-testing/src/test/scala/example/testing/TestingWebSocketCommunication.scala")
```

## See Also

- [TestServer](./test-server.md) — Full reference for integration testing
- [TestClient](./test-client.md) — Full reference for mocking HTTP dependencies
- [TestChannel](./test-channel.md) — Full reference for WebSocket testing
- [HttpTestAspect](./http-test-aspect.md) — Full reference for test aspects
- [Running the Examples](./examples.md) — Runnable test examples and how to execute them
- [Testing HTTP Applications](../../guides/testing-http-apps.md) — Comprehensive how-to guide with patterns
