---
id: test-client
title: "TestClient"
---

`TestClient` is an in-memory HTTP client driver for mocking external API dependencies in tests. Instead of making real HTTP calls to external services, TestClient intercepts requests and returns configured responses. All communication happens in-memory and synchronously, enabling fast, deterministic tests without external dependencies.

The `TestClient` type provides:

```scala mdoc:compile-only
final case class TestClient(
  behavior: Ref[Routes[Any, Response]],
  serverSocketBehavior: Ref[WebSocketApp[Any]],
  missingRouteHandler: Ref[Handler[Any, Response, Request, Response]],
) extends ZClient.Driver[Any, Scope, Throwable] {
  def addRoute[R](route: Route[R, Response]): ZIO[R, Nothing, Unit]
  def addRoutes[R](route: Route[R, Response], routes: Route[R, Response]*): ZIO[R, Nothing, Unit]
  def addRequestResponse(expectedRequest: Request, response: Response): ZIO[Any, Nothing, Unit]
  def setFallbackHandler[R](fallbackFunction: Request => ZIO[R, Response, Response]): ZIO[R, Nothing, Unit]
  def installSocketApp[Env1](app: WebSocketApp[Any]): ZIO[Env1, Nothing, Unit]
}
```

Key properties:
- **Mocking Client** — Implements `ZClient.Driver` interface, works as a drop-in `Client` replacement
- **In-Memory Responses** — Returns configured responses without network I/O
- **Dynamic Route Configuration** — Add routes, request/response pairs, or handlers during test execution
- **Fallback Handling** — Optional fallback handler for unexpected requests
- **WebSocket Support** — Can mock WebSocket server endpoints via `TestClient#installSocketApp`

### Role in Module

`TestClient` is the **primary type for mocking external dependencies** in zio-http-testkit. It mocks the HTTP client to simulate external API calls your application makes.

**Typically used with:** Routes (what external API should respond with), Handler (how to compute responses), TestServer (together when testing full request/response flows)

**Complementary types:**
- TestServer — For testing your own routes and handlers
- TestChannel — For testing WebSocket communication
- HttpTestAspect — For testing mode-dependent behavior

## Motivation

Many applications depend on external HTTP APIs: payment processors, auth services, third-party data sources. Testing code that calls these APIs is challenging:

1. **Real API calls are slow** — Each request waits for network I/O, making tests slow (seconds per test)
2. **Real APIs are unreliable** — External services may go down, become rate-limited, or be unavailable during CI
3. **Hard to test edge cases** — How do you test timeout behavior, 5xx errors, or rate-limit responses without actually calling the API?
4. **Mocking is naive** — Hand-mocked responses often diverge from real API behavior, letting bugs slip through

`TestClient` solves this by intercepting HTTP calls and returning configured responses in-memory, letting you mock external APIs while still exercising the complete HTTP flow with realistic request/response handling.

Use `TestClient` when your code makes HTTP calls to external services and you want to mock responses, verify request correctness, test error handling, or run tests without external service dependencies.

## Quick Showcase

Here's a complete example: configure TestClient to mock an external API, then verify your code calls it correctly:

```scala mdoc:reset
import zio._
import zio.http._

val test = for {
  // TestClient is automatically provided as Client
  client <- ZIO.service[Client]
  
  // 1. Mock external API response
  _ <- TestClient.addRequestResponse(
    Request.get(URL.root / "external-api" / "users" / "123"),
    Response.text("""{"id": 123, "name": "Alice"}""")
  )
  
  // 2. Your code calls the external API (intercepted by TestClient)
  resp <- client(Request.get(URL.root / "external-api" / "users" / "123"))
  body <- resp.body.asString
} yield body

// Result: """{"id": 123, "name": "Alice"}"""
```

## Construction / Creating TestClient

TestClient instances are created via ZIO layers that provide both `TestClient` and `Client` services:

### `TestClient.layer` — Basic TestClient Layer

```scala mdoc:compile-only
val testClientLayer: ZLayer[Any, Nothing, TestClient & Client] = TestClient.layer
```

This creates a TestClient instance with an empty behavior (no routes configured) and a default fallback handler that logs warnings for unexpected requests.

To use `TestClient.layer`, set up routes and make requests:

```scala mdoc:compile-only
import zio._
import zio.http._

val test = for {
  client <- ZIO.service[Client]
  
  // Add mock responses
  _ <- TestClient.addRequestResponse(
    Request.get(URL.root / "api"),
    Response.ok
  )
  
  resp <- client(Request.get(URL.root / "api"))
} yield resp.status

val result = test.provideLayer(TestClient.layer >+> Scope.default)
```

Key behavior:
- Provides both `TestClient` and `Client` services (since TestClient implements the Client interface)
- Default fallback logs warnings for unexpected requests
- Routes configuration starts empty

### `TestClient.withFallbackHandler` — Custom Fallback Behavior

```scala mdoc:compile-only
def withFallbackHandler[R](
  fallbackHandler: Request => ZIO[R, Response, Response]
): ZLayer[R, Nothing, TestClient & Client]
```

Creates TestClient with a custom fallback handler for unexpected requests. Useful for testing error handling or providing default behavior.

```scala mdoc:compile-only
import zio._
import zio.http._

val customFallbackLayer = TestClient.withFallbackHandler { (req: Request) =>
  // Custom fallback: return 404 for unexpected requests
  ZIO.succeed(Response.notFound)
}

val test = for {
  client <- ZIO.service[Client]
  
  // Add one mock response
  _ <- TestClient.addRequestResponse(
    Request.get(URL.root / "known"),
    Response.ok
  )
  
  // Expected request succeeds
  resp1 <- client(Request.get(URL.root / "known"))
  
  // Unexpected request uses fallback (404)
  resp2 <- client(Request.get(URL.root / "unknown"))
} yield (resp1.status, resp2.status)

val result = test.provideLayer(customFallbackLayer >+> Scope.default)
```

## Core Operations

### Route Configuration Group

Configure how TestClient responds to HTTP requests using these methods:

#### `TestClient#addRoute` — Add Dynamic Route Handler

```scala mdoc:compile-only
trait TestClient {
  def addRoute[R](route: Route[R, Response]): ZIO[R, Nothing, Unit]
}
```

Add a route with a handler function that computes responses dynamically based on the request. This is useful for logic-based responses:

```scala mdoc:reset
import zio._
import zio.http._

val test = for {
  client <- ZIO.service[Client]
  
  // Add a dynamic route that extracts path parameter
  _ <- TestClient.addRoute {
    Method.GET / "users" / int("id") -> handler { (id: Int, _: Request) =>
      Response.text(s"""{"id": $id, "name": "User $id"}""")
    }
  }
  
  resp <- client(Request.get(URL.root / "users" / "42"))
  body <- resp.body.asString
} yield body

// Result: """{"id": 42, "name": "User 42"}"""
```

Key behavior:
- Route handlers execute in the request context and can access request data
- Performance: O(1) route matching, same as HTTP routes
- Accumulates with existing routes; first match wins

#### `TestClient#addRoutes` — Add Multiple Dynamic Routes

```scala mdoc:compile-only
trait TestClient {
  def addRoutes[R](
    route: Route[R, Response],
    routes: Route[R, Response]*
  ): ZIO[R, Nothing, Unit]
}
```

Add multiple routes with handler functions. This is useful for comprehensive API mocking:

```scala mdoc:reset
import zio._
import zio.http._

val test = for {
  client <- ZIO.service[Client]
  
  // Mock a complete external API
  _ <- TestClient.addRoutes(
    Method.GET / "users" -> handler { Response.text("[user1, user2]") },
    Method.GET / "users" / int("id") -> handler { (id: Int, _: Request) =>
      Response.text(s"User $id")
    },
    Method.POST / "users" -> handler { Response.status(Status.Created) }
  )
  
  listResp <- client(Request.get(URL.root / "users"))
  oneResp <- client(Request.get(URL.root / "users" / "5"))
  createResp <- client(Request.post(URL.root / "users", Body.empty))
} yield (listResp.status, oneResp.status, createResp.status)

// Result: (Status.Ok, Status.Ok, Status.Created)
```

Key behavior:
- All routes added together
- Route matching follows HTTP routing rules

#### `TestClient#addRequestResponse` — Exact Request/Response Matching

```scala mdoc:compile-only
trait TestClient {
  def addRequestResponse(
    expectedRequest: Request,
    response: Response
  ): ZIO[Any, Nothing, Unit]
}
```

Define a 1-1 mapping between an exact request and a fixed response. This is the simplest form of mocking for fixed scenarios. Matches on:
- **Method** — Must match exactly
- **Path** — Must match exactly
- **Headers** — Expected request headers must all be present (actual request can have additional)

The request must match exactly, or the TestClient throws `MatchError`:

```scala mdoc:reset
import zio._
import zio.http._

val test = for {
  client <- ZIO.service[Client]
  
  // Mock exact request/response
  apiUrl = URL.root / "api" / "data"
  apiReq = Request.get(apiUrl)
  apiResp = Response.text("Expected data")
  _ <- TestClient.addRequestResponse(apiReq, apiResp)
  
  // Exact request succeeds
  resp1 <- client(apiReq)
  body1 <- resp1.body.asString
} yield body1

// Result: "Expected data"
```

Key behavior:
- Throws `MatchError` if request doesn't match (use dynamic routes for flexible matching)
- Useful for simple "API always returns X" scenarios
- Strict matching ensures tests catch changes in request format

#### `TestClient#setFallbackHandler` — Fallback for Unmatched Requests

```scala mdoc:compile-only
trait TestClient {
  def setFallbackHandler[R](
    fallbackFunction: Request => ZIO[R, Response, Response]
  ): ZIO[R, Nothing, Unit]
}
```

Set a handler for requests that don't match any configured route. This is useful for default behavior or error handling:

```scala mdoc:reset
import zio._
import zio.http._

val test = for {
  client <- ZIO.service[Client]
  
  // Track unexpected requests
  unexpected <- Ref.make[List[String]](List.empty)
  
  // Set fallback to track and return error
  _ <- TestClient.setFallbackHandler { (req: Request) =>
    for {
      _ <- unexpected.update(_ :+ req.url.toString)
    } yield Response.status(Status.ServiceUnavailable)
  }
  
  // Add one expected route
  _ <- TestClient.addRoute {
    Method.GET / "expected" -> handler { Response.ok }
  }
  
  // Expected request succeeds
  resp1 <- client(Request.get(URL.root / "expected"))
  
  // Unexpected request uses fallback
  resp2 <- client(Request.get(URL.root / "unexpected"))
  
  unexpectedList <- unexpected.get
} yield (resp1.status, resp2.status, unexpectedList.length)

// Result: (Status.Ok, Status.ServiceUnavailable, 1)
```

Key behavior:
- Replaces the default fallback handler
- Useful for testing error handling or capturing unexpected requests
- Executes in the request context; can inspect request details

### WebSocket Support

#### `TestClient#installSocketApp` — Mock WebSocket Server

```scala mdoc:compile-only
trait TestClient {
  def installSocketApp[Env1](app: WebSocketApp[Any]): ZIO[Env1, Nothing, Unit]
}
```

Use `installSocketApp` to configure TestClient to handle WebSocket upgrade requests. When your code initiates a WebSocket connection, TestClient runs the configured WebSocket app, allowing bidirectional message exchange via TestChannel:

```scala mdoc:compile-only
import zio._
import zio.http._

val test = for {
  client <- ZIO.service[Client]
  
  // Mock WebSocket server with echo behavior
  _ <- TestClient.installSocketApp {
    Handler.webSocket { channel =>
      channel.receiveAll { msg =>
        channel.send(msg)  // Echo back
      }
    }
  }
  
  // Your code initiates WebSocket connection
  // Communication happens via TestChannel
} yield ()
```

Key behavior:
- Handles WebSocket upgrade handshakes automatically
- Messages flow bidirectionally through TestChannel
- Both client and server handlers run concurrently

## Common Patterns

This section shows practical patterns for using TestClient.

### Mocking External REST API

Test code that calls an external REST API:

```scala mdoc:reset
import zio._
import zio.http._

val test = for {
  client <- ZIO.service[Client]
  
  // Mock external payment API
  _ <- TestClient.addRoute(
    Method.POST / "api" / "payments" -> handler { Response.text("""{"status": "success", "transactionId": "12345"}""") }
  )
  
  // Your application code calls the mocked API
  paymentReq = Request.post(URL.root / "api" / "payments", Body.fromString("""{"amount": 100}"""))
  paymentResp <- client(paymentReq)
  paymentBody <- paymentResp.body.asString
} yield paymentBody
```

### Testing Error Handling

Test how your code handles API errors:

```scala mdoc:reset
import zio._
import zio.http._

val test = for {
  client <- ZIO.service[Client]
  
  // Mock API that returns 5xx error
  _ <- TestClient.addRoute {
    Method.GET / "api" / "unstable" -> handler {
      Response.status(Status.InternalServerError)
    }
  }
  
  // Your code should handle the error gracefully
  resp <- client(Request.get(URL.root / "api" / "unstable"))
  status = resp.status
} yield status

// Result: Status.InternalServerError
```

### Multiple Mock APIs

Test code that integrates multiple external services:

```scala mdoc:reset
import zio._
import zio.http._

val test = for {
  client <- ZIO.service[Client]
  
  // Mock first external API
  _ <- TestClient.addRoute {
    Method.GET / "auth-api" / "validate" -> handler {
      Response.text("""{"valid": true}""")
    }
  }
  
  // Mock second external API
  _ <- TestClient.addRoute {
    Method.GET / "data-api" / "fetch" -> handler {
      Response.text("""{"data": "content"}""")
    }
  }
  
  // Your code calls both APIs
  authResp <- client(Request.get(URL.root / "auth-api" / "validate"))
  dataResp <- client(Request.get(URL.root / "data-api" / "fetch"))
} yield (authResp.status, dataResp.status)
```

## Integration with Other Types

### Within Module

TestClient and TestServer work together in several ways.

**`TestServer`** — TestClient and TestServer work together:
- TestServer tests your routes
- TestClient mocks external APIs your routes call

Example: TestServer handler calls mocked external API:

```scala mdoc:compile-only
import zio._
import zio.http._

val test = for {
  client <- ZIO.service[Client]
  serverPort <- ZIO.serviceWithZIO[Server](_.port)
  
  // Mock external API with TestClient
  _ <- TestClient.addRoute {
    Method.GET / "external" -> handler { Response.text("External data") }
  }
  
  // Handler in TestServer calls mocked external API
  _ <- TestServer.addRoute {
    Method.GET / "aggregate" -> handler { Response.text("Aggregated: External data") }
  }
  
  // Test the complete flow
  resp <- client(Request.get(URL.root.port(serverPort) / "aggregate"))
} yield resp.status
```

**`TestChannel`** — TestClient handles WebSocket via installSocketApp, using TestChannel underneath.

**`HttpTestAspect`** — Apply mode-dependent behavior testing to TestClient routes.

### External Modules

- **zio-http core** — TestClient uses `Request`, `Response`, `Client` types from the main HTTP library
- **zio** — Uses `ZIO`, `Ref`, `ZLayer` for effect management and test setup
- **zio-http netty** — Depends on Netty driver layer for protocol support

## API Reference

### Public Instance Methods

| Method | Signature | Purpose |
|--------|-----------|---------|
| `TestClient#addRoute` | `[R] Route[R, Response] => ZIO[R, Nothing, Unit]` | Add dynamic route handler |
| `TestClient#addRoutes` | `[R] Route[R, Response] + Routes[R, Response]* => ZIO[R, Nothing, Unit]` | Add multiple routes |
| `TestClient#addRequestResponse` | `Request, Response => ZIO[Any, Nothing, Unit]` | Add exact request/response mapping |
| `TestClient#setFallbackHandler` | `[R] (Request => ZIO[R, Response, Response]) => ZIO[R, Nothing, Unit]` | Set fallback for unmatched requests |
| `TestClient#installSocketApp` | `[Env] WebSocketApp[Any] => ZIO[Env, Nothing, Unit]` | Configure WebSocket handler |

### Companion Object Methods

| Method | Signature | Purpose |
|--------|-----------|---------|
| `TestClient.layer` | `ZLayer[Any, Nothing, TestClient & Client]` | Create TestClient layer |
| `TestClient.withFallbackHandler` | `[R] (Request => ZIO[R, Response, Response]) => ZLayer[R, Nothing, TestClient & Client]` | Create TestClient with custom fallback |
| `TestClient#addRoute` | `[R] Route[R, Response] => ZIO[R with TestClient, Nothing, Unit]` | Service method for adding route |
| `TestClient#addRoutes` | `[R] Route[R, Response] + Routes[R, Response]* => ZIO[R with TestClient, Nothing, Unit]` | Service method for adding routes |
| `TestClient#addRequestResponse` | `Request, Response => ZIO[TestClient, Nothing, Unit]` | Service method for request/response |
| `TestClient#setFallbackHandler` | `[R] (Request => ZIO[R, Response, Response]) => ZIO[R with TestClient, Nothing, Unit]` | Service method for fallback |
| `TestClient#installSocketApp` | `WebSocketApp[Any] => ZIO[TestClient, Nothing, Unit]` | Service method for WebSocket |

## See Also

- [TestServer](./test-server.md) — Testing your own routes and handlers
- [TestChannel](./test-channel.md) — Testing WebSocket handlers
- [HttpTestAspect](./http-test-aspect.md) — Testing mode-dependent behavior
