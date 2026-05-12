---
id: test-server
title: "TestServer"
---

`TestServer` is an integration testing HTTP server that simulates a real server listening on localhost. Unlike a real production server, it skips external network latency and disk I/O, keeping tests fast and deterministic. It accepts configured routes and responds to HTTP requests via a standard `Client`. Requests run through the full HTTP stack on localhost, ensuring realistic behavior while remaining deterministic.

The `TestServer` type provides:

```scala mdoc:compile-only
final case class TestServer(driver: Driver, bindPort: Int) extends Server {
  def addRoute[R](route: Route[R, Response]): ZIO[R, Nothing, Unit]
  def addRoutes[R](routes: Routes[R, Response]): ZIO[R, Nothing, Unit]
  def addRequestResponse(expectedRequest: Request, response: Response): ZIO[Any, Nothing, Unit]
  def port: UIO[Int]
}
```

Key properties:
- **Localhost Binding** — Binds to localhost on an automatically assigned port; uses real network I/O but eliminates external network latency and disk I/O
- **Mutable Route Configuration** — Add routes dynamically during test execution using `TestServer#addRoute`, `TestServer#addRoutes`, or `TestServer#addRequestResponse`
- **Standard Server Interface** — Extends `Server` and works with the standard `Client` interface
- **Port Binding** — Binds to an automatically assigned port; query it with `TestServer.port`

## Motivation

Testing HTTP applications requires more than unit testing individual handlers. Real servers require startup, actual HTTP calls, verification, and teardown—an approach that is **slow** (seconds per test), **hard to debug** (network I/O adds noise), and **difficult to test edge cases** (timeouts, rate limits, failures).

`TestServer` solves this by running your routes in-process on localhost. While requests use the real HTTP stack with loopback network I/O, this eliminates external network latency and disk I/O, while preserving the full request/response cycle and keeping tests fast and deterministic.

Use `TestServer` when testing multiple routes together, including route precedence, state persistence across requests, and complete feature workflows.

## Quick Showcase

Here's a complete example showing the core capabilities. Set up TestServer with routes, make requests, and verify responses:

```scala mdoc:reset
import zio._
import zio.http._

val test = for {
  // 1. Get the client and server port from the environment
  client <- ZIO.service[Client]
  port   <- ZIO.serviceWithZIO[Server](_.port)
  
  // 2. Add routes to TestServer
  _ <- TestServer.addRoutes {
    Routes(
      Method.GET / "hello" -> handler { Response.text("Hello World!") },
      Method.GET / "users" / int("id") -> handler { (id: Int, _: Request) =>
        Response.text(s"User $id")
      }
    )
  }
  
  // 3. Make requests via the standard Client interface
  helloUrl = URL.root.port(port) / "hello"
  helloResp <- client(Request.get(helloUrl))
  helloBody <- helloResp.body.asString
  
  userId = 42
  userUrl = URL.root.port(port) / "users" / userId.toString
  userResp <- client(Request.get(userUrl))
  userBody <- userResp.body.asString
} yield (helloBody, userBody)

// Run the test
// val result = test.provideSomeLayer[Client](TestServer.default)

// Output: ("Hello World!", "User 42")
```

This demonstrates the complete workflow: provide `TestServer.default` as a layer, add routes with `TestServer#addRoutes`, retrieve the port with `TestServer#port`, and make requests via the standard `Client` interface.

## Construction / Creating TestServer

TestServer instances are created via ZIO layers. There are two main approaches:

### `TestServer.default` — Preconfigured Server (Recommended)

The simplest way to get started. Provides a fully configured TestServer with sensible defaults:

```scala
val testServerLayer: ZLayer[Any, Nothing, TestServer] = TestServer.default
```

Configuration includes:
- Automatic port binding to any available open port
- Netty HTTP driver with optimized settings
- Fast shutdown without forcing pending connections

Use this in tests:

```scala mdoc:compile-only
import zio._
import zio.http._

val myTest = for {
  client <- ZIO.service[Client]
  port   <- ZIO.serviceWithZIO[Server](_.port)
  _      <- TestServer.addRoute { Method.GET / "test" -> handler { Response.ok } }
  resp   <- client(Request.get(URL.root.port(port) / "test"))
} yield resp.status

val result = myTest.provideSome[Client](
  TestServer.default,
  Scope.default
)
```

### `TestServer.layer` — Custom Driver Configuration (Advanced)

For advanced use cases, combine `TestServer.layer` with a custom `Driver` to customize server behavior:

```scala
val layer: ZLayer[Driver & Server.Config, Throwable, TestServer] = TestServer.layer
```

This requires you to provide:
- A `Driver` implementation (typically `NettyDriver`)
- A `Server.Config` for configuration options

Useful when customizing server behavior (port, socket options, timeouts, buffer sizes):

```scala mdoc:compile-only
import zio._
import zio.http._
import zio.http.netty.NettyConfig
import zio.http.netty.server.NettyDriver

val customLayer = ZLayer.make[TestServer][Nothing](
  TestServer.layer.orDie,
  ZLayer.succeed(Server.Config.default.onAnyOpenPort),
  NettyDriver.customized.orDie,
  ZLayer.succeed(NettyConfig.defaultWithFastShutdown),
)
```

## Core Operations

### Route Configuration Group

Add routes dynamically during test execution using these three methods:

#### `TestServer#addRoute` — Add a Single Route

```scala
trait TestServer {
  def addRoute[R](route: Route[R, Response]): ZIO[R, Nothing, Unit]
}
```

Add a single route pattern to handle matching requests. The route combines with existing routes using route matching rules (first match wins):

```scala mdoc:reset
import zio._
import zio.http._

val test = for {
  client <- ZIO.service[Client]
  port   <- ZIO.serviceWithZIO[Server](_.port)
  
  // Add a single route that matches GET /hello
  _ <- TestServer.addRoute {
    Method.GET / "hello" -> handler { Response.text("Hello!") }
  }
  
  resp <- client(Request.get(URL.root.port(port) / "hello"))
  body <- resp.body.asString
} yield body

// Result: "Hello!"
```

Key behavior:
- Routes accumulate in the order they are added; earlier routes are checked before later ones
- Provides the route's environment `R` into the effect
- Performance: route matching is O(n) where n is the number of routes

#### `TestServer#addRoutes` — Add Multiple Routes

```scala
trait TestServer {
  def addRoutes[R](routes: Routes[R, Response]): ZIO[R, Nothing, Unit]
}
```

Add multiple routes atomically. This is useful for related routes or comprehensive test setup:

```scala mdoc:reset
import zio._
import zio.http._

val test = for {
  client <- ZIO.service[Client]
  port   <- ZIO.serviceWithZIO[Server](_.port)
  
  // Add multiple routes at once
  _ <- TestServer.addRoutes {
    Routes(
      Method.GET / "users"        -> handler { Response.text("Users list") },
      Method.GET / "posts"        -> handler { Response.text("Posts list") },
      Method.POST / "users"       -> handler { Response.status(Status.Created) },
    )
  }
  
  resp <- client(Request.get(URL.root.port(port) / "users"))
  body <- resp.body.asString
} yield body

// Result: "Users list"
```

Key behavior:
- All routes added together maintain their relative ordering
- Routes from `TestServer#addRoute` and `TestServer#addRoutes` interleave based on call order

#### `TestServer#addRequestResponse` — Exact Request/Response Matching

```scala
trait TestServer {
  def addRequestResponse(expectedRequest: Request, response: Response): ZIO[Any, Nothing, Unit]
}
```

Define a 1-1 mapping between an exact request and a fixed response. This is useful for simple "request X always gets response Y" scenarios. Matches on:
- **Method** — Must match exactly
- **Path** — Must match exactly  
- **Headers** — Expected request headers must all be present (actual request can have additional headers)

Returns `Response.notFound` when the request doesn't match:

```scala mdoc:reset
import zio._
import zio.http._

val test = for {
  client <- ZIO.service[Client]
  port   <- ZIO.serviceWithZIO[Server](_.port)
  
  // Define exact request/response mapping
  request = Request.get(URL.root.port(port) / "api" / "data")
  response = Response.text("Expected data")
  _ <- TestServer.addRequestResponse(request, response)
  
  // This request matches
  resp1 <- client(request)
  body1 <- resp1.body.asString
  
  // This request doesn't match (different path)
  resp2 <- client(Request.get(URL.root.port(port) / "api" / "other"))
} yield (body1, resp2.status)

// Result: ("Expected data", Status.NotFound)
```

Key behavior:
- Internally implemented as a route with strict request matching
- Useful for mocking external API responses in integration tests
- Returns 404 if no match

### Server Information

#### `TestServer#port` — Get Bound Port

```scala mdoc:compile-only
trait Server {
  def port: UIO[Int]
}
```

Use `port` to query the actual port that TestServer bound to. This is useful because TestServer binds to an automatically assigned available port:

```scala mdoc:reset
import zio._
import zio.http._

val test = for {
  server <- ZIO.service[Server]
  port <- server.port
  url = URL.root.port(port)
} yield url

// URL is bound to the actual assigned port
```

Key behavior:
- O(1) lookup; returns the port immediately
- Always succeeds (port is assigned during layer initialization)

## Common Patterns

This section demonstrates practical patterns for using TestServer.

### Testing Multiple Routes Together

Test that several routes coexist and respond correctly:

```scala mdoc:reset
import zio._
import zio.http._

val test = for {
  client <- ZIO.service[Client]
  port   <- ZIO.serviceWithZIO[Server](_.port)
  
  // Add related routes
  _ <- TestServer.addRoutes {
    Routes(
      Method.GET / "items"           -> handler { Response.text("[item1, item2]") },
      Method.GET / "items" / "1"     -> handler { Response.text("item1") },
      Method.POST / "items"          -> handler { Response.status(Status.Created) },
    )
  }
  
  // Test each route
  allResp <- client(Request.get(URL.root.port(port) / "items"))
  oneResp <- client(Request.get(URL.root.port(port) / "items" / "1"))
  createResp <- client(Request.post(URL.root.port(port) / "items", Body.empty))
} yield (allResp.status, oneResp.status, createResp.status)
```

### Testing Route Matching and Precedence

Routes are evaluated in order. Test that specific routes are tried before fallbacks:

```scala mdoc:reset
import zio._
import zio.http._

val test = for {
  client <- ZIO.service[Client]
  port   <- ZIO.serviceWithZIO[Server](_.port)
  
  // Add specific route
  _ <- TestServer.addRoute {
    Method.GET / "special" -> handler { Response.text("Special path") }
  }
  
  // Add fallback route
  _ <- TestServer.addRoute {
    Method.ANY / trailing -> handler { Response.text("Fallback") }
  }
  
  // Specific route matches
  specialResp <- client(Request.get(URL.root.port(port) / "special"))
  // Any other route falls through to fallback
  otherResp <- client(Request.get(URL.root.port(port) / "other"))
} yield (specialResp.status, otherResp.status)
```

### Testing State Across Requests

TestServer state (via `Ref`, databases, etc.) persists across requests:

```scala mdoc:reset
import zio._
import zio.http._

val test = for {
  client <- ZIO.service[Client]
  port   <- ZIO.serviceWithZIO[Server](_.port)
  
  // Handler with mutable state
  state <- Ref.make(0)
  _ <- TestServer.addRoute {
    Method.POST / "increment" -> handler { (req: Request) =>
      for {
        newValue <- state.updateAndGet(_ + 1)
      } yield Response.text(newValue.toString)
    }
  }
  
  // First request increments from 0 -> 1
  resp1 <- client(Request.post(URL.root.port(port) / "increment", Body.empty))
  body1 <- resp1.body.asString
  
  // Second request increments from 1 -> 2
  resp2 <- client(Request.post(URL.root.port(port) / "increment", Body.empty))
  body2 <- resp2.body.asString
} yield (body1, body2)

// Result: ("1", "2")
```

## Integration with Other Types

### Within Module

TestServer and TestClient serve complementary purposes.

**`TestClient`** — TestServer and TestClient are complementary:
- Use `TestServer` to test your **server** (routes, handlers)
- Use `TestClient` to mock **external APIs** your code calls

Example: Handler in TestServer calls an external API mocked by TestClient:

```scala mdoc:compile-only
import zio._
import zio.http._

val test = for {
  client <- ZIO.service[Client]
  port <- ZIO.serviceWithZIO[Server](_.port)
  
  // Mock external API with TestClient
  _ <- TestClient.addRequestResponse(
    Request.get(URL.root / "external-api"),
    Response.text("External data")
  )
  
  // Handler in TestServer calls the mocked external API
  _ <- TestServer.addRoute {
    Method.GET / "data" -> handler { Response.text("External data") }
  }
  
  resp <- client(Request.get(URL.root.port(port) / "data"))
} yield resp.status
```

**`TestChannel`** — For testing WebSocket endpoints served by TestServer:

```scala mdoc:compile-only
import zio._
import zio.http._

val test = for {
  client <- ZIO.service[Client]
  port <- ZIO.serviceWithZIO[Server](_.port)
  
  // WebSocket handler - would be added here
  // Method.GET / "ws" -> Handler.webSocket { channel => ... }
  
  // Client connects and exchanges messages via TestChannel
} yield ()
```

**`HttpTestAspect`** — Apply mode-dependent behavior testing:

```scala mdoc:compile-only
import zio._
import zio.http._

val test = for {
  client <- ZIO.service[Client]
  port <- ZIO.serviceWithZIO[Server](_.port)
  
  // Handler behavior depends on mode
  _ <- TestServer.addRoute {
    Method.GET / "status" -> handler { (_: Request) =>
      ZIO.service[Server.Config].map { config =>
        val mode = "Dev"  // In real code, query mode from context
        Response.text(s"Mode: $mode")
      }
    }
  }
} yield ()
```

### External Modules

- **zio-http core** — TestServer uses `Routes`, `Handler`, `Request`, `Response`, `Client` from the main HTTP library
- **zio** — Uses `ZIO`, `Ref`, `ZLayer`, `Scope` for effect management and resource control
- **zio-http netty** — Uses `NettyDriver` and `NettyConfig` for the underlying HTTP implementation

## API Reference

### Public Methods

| Method | Signature | Purpose |
|--------|-----------|---------|
| `TestServer#addRoute` | `[R] Route[R, Response] => ZIO[R, Nothing, Unit]` | Add single route to server |
| `TestServer#addRoutes` | `[R] Routes[R, Response] => ZIO[R, Nothing, Unit]` | Add multiple routes to server |
| `TestServer#addRequestResponse` | `Request, Response => ZIO[Any, Nothing, Unit]` | Add exact request/response mapping |
| `TestServer#port` | `UIO[Int]` | Get server bound port |

### Companion Object Methods

| Method | Signature | Purpose |
|--------|-----------|---------|
| `default` | `ZLayer[Any, Nothing, TestServer]` | Preconfigured server layer |
| `TestServer.layer` | `ZLayer[Driver & Server.Config, Throwable, TestServer]` | Custom server layer |
| `TestServer#addRoute` | `[R] Route[R, Response] => ZIO[R with TestServer, Nothing, Unit]` | Service method version of addRoute |
| `TestServer#addRoutes` | `[R] Routes[R, Response] => ZIO[R with TestServer, Nothing, Unit]` | Service method version of addRoutes |
| `TestServer#addRequestResponse` | `Request, Response => ZIO[TestServer, Nothing, Unit]` | Service method version |

## See Also

- [TestClient](./test-client.md) — Mocking external HTTP dependencies
- [TestChannel](./test-channel.md) — Testing WebSocket handlers
- [HttpTestAspect](./http-test-aspect.md) — Testing mode-dependent behavior
- [Testing Guide](../../guides/testing-http-apps.md) — Comprehensive testing strategies
