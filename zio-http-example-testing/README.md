# ZIO HTTP Testing Examples

This example project demonstrates all testing patterns covered in the [Testing HTTP Applications guide](../../docs/guides/testing-http-apps.md).

## What's Included

This project contains comprehensive examples for:

1. **Direct Route Testing** (`DirectRouteTestingExamples.scala`)
   - Testing routes as pure functions
   - Path matching and parameterization
   - Different HTTP methods
   - Request body handling

2. **TestClient Examples** (`TestClientExamples.scala`)
   - Mocking HTTP dependencies
   - Simple request/response mappings
   - Flexible route handlers
   - Fallback handlers for tracking unexpected calls
   - Error response mocking

3. **TestServer Examples** (`TestServerExamples.scala`)
   - Integration testing with TestServer
   - Multiple routes working together
   - Route precedence and matching
   - Request parameters and body handling
   - Error handling in servers

4. **Stateful Handler Examples** (`StatefulHandlerExamples.scala`)
   - Maintaining state across requests
   - Authentication and sessions
   - Request tracking and logging
   - Data store patterns
   - State modification patterns

5. **WebSocket Examples** (`WebSocketExamples.scala`)
   - Simple echo server
   - Bidirectional communication
   - Server-initiated messages
   - Different frame types
   - Stateful WebSocket handlers
   - Broadcast patterns

6. **Error Handling Examples** (`ErrorHandlingExamples.scala`)
   - HTTP status codes (404, 401, 403, 500, etc.)
   - Input validation errors
   - Error message quality
   - Edge case handling
   - Graceful degradation

## Running the Examples

### Run all tests:
```bash
sbt test
```

### Run a specific test suite:
```bash
sbt "testOnly example.testing.DirectRouteTestingExamples"
sbt "testOnly example.testing.TestClientExamples"
sbt "testOnly example.testing.TestServerExamples"
sbt "testOnly example.testing.StatefulHandlerExamples"
sbt "testOnly example.testing.WebSocketExamples"
sbt "testOnly example.testing.ErrorHandlingExamples"
```

### Run a specific test:
```bash
sbt "testOnly example.testing.DirectRouteTestingExamples -- -t \"specific route matches\""
```

## Project Structure

```
zio-http-example-testing/
├── build.sbt
├── README.md
└── src/test/scala/example/testing/
    ├── DirectRouteTestingExamples.scala
    ├── TestClientExamples.scala
    ├── TestServerExamples.scala
    ├── StatefulHandlerExamples.scala
    ├── WebSocketExamples.scala
    └── ErrorHandlingExamples.scala
```

## Key Concepts

### Three Testing Patterns

1. **Direct Route Testing** — Test routes as pure functions, no server
   - Fastest feedback loop
   - Good for unit testing handlers
   - No setup required

2. **TestClient** — Mock HTTP client behavior
   - Simulate external service dependencies
   - Verify your code makes correct HTTP calls
   - No actual network I/O

3. **TestServer** — Full integration testing
   - Most realistic: routes actually process requests
   - Test multiple routes working together
   - Good for feature-level testing

### When to Use Each Pattern

| Pattern | Best For | Pros | Cons |
|---------|----------|------|------|
| Direct Route Testing | Unit testing handlers | Fastest, simple | Limited to single handler |
| TestClient | Mocking dependencies | Realistic client behavior | Not for testing your server |
| TestServer | Feature integration tests | Full request/response cycle | Slower than direct testing |

## Learning Path

1. Start with **DirectRouteTestingExamples** to understand basic testing
2. Move to **TestClientExamples** to learn about mocking external services
3. Study **TestServerExamples** for integration testing patterns
4. Explore **StatefulHandlerExamples** for testing state management
5. Learn **WebSocketExamples** for real-time communication patterns
6. Review **ErrorHandlingExamples** for robustness testing

## Common Patterns

### Testing state across requests
```scala
for {
  client <- ZIO.service[Client]
  port   <- ZIO.serviceWithZIO[Server](_.port)
  counter <- Ref.make(0)
  _ <- TestServer.addRoute {
    Method.GET / "increment" -> handler { _ =>
      counter.updateAndGet(_ + 1).map(n => Response.text(s"Count: $n"))
    }
  }
  // Make multiple requests to verify state changes
  resp1 <- client(Request.get(URL.root.port(port) / "increment"))
  resp2 <- client(Request.get(URL.root.port(port) / "increment"))
} yield assertTrue(...)
```

### Testing error paths
```scala
for {
  client <- ZIO.service[Client]
  port   <- ZIO.serviceWithZIO[Server](_.port)
  _ <- TestServer.addRoute {
    Method.GET / "protected" -> handler { req =>
      if (req.headers.contains("Authorization"))
        ZIO.succeed(Response.ok)
      else
        ZIO.succeed(Response.status(Status.Unauthorized))
    }
  }
  // Test without auth
  unauth <- client(Request.get(URL.root.port(port) / "protected"))
  // Test with auth
  withAuth <- client(
    Request.get(URL.root.port(port) / "protected")
      .addHeader("Authorization", "Bearer token")
  )
} yield assertTrue(unauth.status == Status.Unauthorized, withAuth.status == Status.Ok)
```

## Reference

For detailed explanations of each pattern, see:
- [Testing HTTP Applications Guide](../../docs/guides/testing-http-apps.md)
- [ZIO HTTP Documentation](https://ziohttp.dev)
- [ZIO Test Documentation](https://zio.dev/reference/test/)
