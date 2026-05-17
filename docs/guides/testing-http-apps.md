---
id: testing-http-apps
title: Testing HTTP Applications
---

## Introduction

Testing HTTP applications is fundamentally different from testing regular Scala libraries — you're testing code that handles network I/O, manages stateful connections, and deals with concurrent requests. This guide shows you how to write fast, reliable tests for ZIO HTTP applications without the typical overhead and complexity of HTTP testing.

The key insight is that **ZIO HTTP treats routes as pure functions**: a route is simply `Request => ZIO[R, Response, Response]`. This functional model means you can test your HTTP logic the same way you test any other ZIO effect — by providing inputs and asserting on outputs — without needing to start a server, manage ports, or deal with network I/O.

By the end of this guide, you'll understand:
- How to test routes directly without any infrastructure
- How to use `TestClient` to mock HTTP client behavior
- How to use `TestServer` for integration testing multiple routes
- How to test stateful handlers that maintain request context
- How to test WebSocket connections with bidirectional messaging
- How to verify error handling and edge cases

## The Problem with Traditional HTTP Testing

Before ZIO HTTP, testing an HTTP service typically involved one of these painful approaches:

**Starting a real server for each test**: Your test suite starts a Netty server on a random port, makes HTTP requests to it, then shuts it down. This works, but is slow (seconds per test), hard to debug (now you have network I/O to reason about), and makes it difficult to test edge cases (how do you simulate a network timeout? A broken connection?).

**Mocking everything at the HTTP library level**: You might mock the underlying `Client` or `Server` classes, but then you're not really testing your HTTP logic — you're testing that your code calls the mocked object correctly. If the mock doesn't match the real behavior, your tests pass but your code fails in production.

**Writing integration tests that depend on external services**: Your tests talk to a real database, a real cache, a real message queue. Now your tests are slow, flaky (external services go down), and hard to run locally. You can't easily test error paths like "what if the database is unavailable?"

ZIO HTTP solves this by treating routes as **pure, testable functions** that you can invoke directly in your tests without any server infrastructure.

## Prerequisites and Setup

To follow this guide, add these test dependencies to your `build.sbt`:

```scala
libraryDependencies ++= Seq(
  "dev.zio" %% "zio-test"         % "@ZIO_VERSION@"  % Test,
  "dev.zio" %% "zio-test-sbt"     % "@ZIO_VERSION@"  % Test,
  "dev.zio" %% "zio-http-testkit" % "@VERSION@"      % Test,
)
testFrameworks += new TestFramework("zio.test.sbt.ZTestFramework")
```

This guide assumes you're familiar with:
- [ZIO effects and testing](https://zio.dev/reference/test/) — how to write ZIO code and tests
- [ZIO HTTP routes and handlers](../reference/routing/routes.md) — how to define HTTP endpoints
- Basic HTTP concepts — requests, responses, status codes, headers

If you're new to ZIO or ZIO HTTP, start with those reference pages before diving into testing patterns.

## Three Testing Patterns

ZIO HTTP provides three distinct testing patterns, each suited to different scenarios:

1. **Direct Route Testing**: Test a route function directly by calling `routes.runZIO(request)`. This is the simplest approach and ideal for unit testing individual handlers in isolation.

2. **TestClient**: Mock the HTTP client by defining what responses to return for specific requests. Use when your application makes HTTP calls to external services and you want to mock those dependencies.

3. **TestServer**: Start a test server that responds to HTTP requests based on your routes. Use for integration testing multiple routes working together, or when testing code that makes HTTP requests and you want to verify the exact requests being made.

Each pattern serves a different testing need — we'll explore them in order of complexity and show when to use each one.

## Pattern 1: Direct Route Testing

The simplest and fastest way to test is to invoke a route directly as a function, without any server infrastructure. In ZIO HTTP, a `Routes` object is just a function that takes a `Request` and returns a `ZIO` effect that produces a `Response`. You can call this function directly in your test.

This approach is ideal when you're testing a single handler in isolation — for example, a handler that parses JSON, validates input, and returns a response. There's no networking, no port binding, no concurrent connections to worry about. Just pure ZIO effects.

**When to use this pattern:**
- Unit testing individual handlers or middleware
- Testing request parsing and validation logic
- Testing simple routes with no external dependencies
- Fast feedback loop during development

**When NOT to use this pattern:**
- Your code needs to handle multiple routes with different path patterns
- You need to test that your code makes HTTP calls to other services
- You're testing middleware or request/response transformations that depend on route context

Here's a simple example:

```scala mdoc:passthrough
import utils._

printSource("zio-http-example-testing/src/test/scala/example/testing/DirectRouteExampleSpec.scala")
```

([source](https://github.com/zio/zio-http/blob/main/zio-http-example-testing/src/test/scala/example/testing/DirectRouteExampleSpec.scala))

The key here is `routes.runZIO(request)` — this invokes the route function directly and returns the response as a ZIO effect, which you then assert on.

**Behind the scenes:** Routes are implemented as a function that pattern-matches on the request path and method, then invokes the appropriate handler. By calling `runZIO` directly, you skip the server entirely and invoke the routing logic as a pure function.

## Pattern 2: TestClient — Mock HTTP Dependencies

`TestClient` is a mock HTTP client implementation. Instead of making real HTTP requests to external services, your code makes requests to the `TestClient`, which you've configured to return specific responses.

**What is TestClient useful for?** 

Imagine your application calls an external service — maybe a payment processor, a weather API, or a recommendation engine. In production, these calls go over the network to real servers. But in tests, you don't want to:
- Make real requests to external APIs (slow, unreliable, might incur costs)
- Depend on those services being up and available
- Test against production data or state

Instead, you use `TestClient` to provide a mock implementation of the `Client` interface. Your code calls the mock client with requests, and the mock returns responses you've configured.

**How TestClient works:**

You provide the `TestClient.layer` in your test, which gives your code a `Client` instance. Instead of the real network-based client, it's a test implementation. Then you configure what responses to return for what requests using methods like `addRoute`, `addRequestResponse`, and `setFallbackHandler`.

**Simple request/response mappings:**

For straightforward cases where a specific request should always get a specific response, use `addRequestResponse`:

```scala mdoc:passthrough
import utils._

printSource("zio-http-example-testing/src/test/scala/example/testing/GuideTestClientBasicSpec.scala")
```

([source](https://github.com/zio/zio-http/blob/main/zio-http-example-testing/src/test/scala/example/testing/GuideTestClientBasicSpec.scala))

The mock checks that the incoming request matches exactly — same method, same URL, same headers. If your code makes a different request, it throws an error. This strictness is actually a feature: it forces your test to be specific about what requests should be made.

**Flexible route handlers:**

Sometimes you need more flexibility. Maybe you want to handle a range of requests (e.g., `GET /users/{id}` for any ID), or perform some computation before returning the response. Use `addRoute`:

```scala mdoc:passthrough
import utils._

printSource("zio-http-example-testing/src/test/scala/example/testing/GuideTestClientFlexibleSpec.scala")
```

([source](https://github.com/zio/zio-http/blob/main/zio-http-example-testing/src/test/scala/example/testing/GuideTestClientFlexibleSpec.scala))

This is more flexible — the handler function lets you compute the response dynamically based on the request content.

**Catching unexpected requests with fallback handlers:**

A powerful testing technique is to track what requests your code makes. Use `setFallbackHandler` to see any requests that don't match your configured routes:

```scala mdoc:passthrough
import utils._

printSource("zio-http-example-testing/src/test/scala/example/testing/GuideFallbackHandlerSpec.scala")
```

([source](https://github.com/zio/zio-http/blob/main/zio-http-example-testing/src/test/scala/example/testing/GuideFallbackHandlerSpec.scala))

This pattern is especially useful when testing code that makes HTTP calls to multiple services. You can verify that:
1. The expected calls are made with the right parameters
2. No unexpected calls are made to unrelated services
3. The code handles specific error responses correctly

:::tip[Inverted Dependency Model]
TestClient is an **inverted dependency model**: instead of mocking the Client interface itself (which is brittle), you configure TestClient routes just like you would configure real server routes. Your code gets a Client that works exactly like the real one, but backed by your test configuration. This makes tests more realistic and resistant to changes in the Client implementation.
:::

## Pattern 3: TestServer — Integration Testing

Use `TestServer` when you need to test your HTTP application as a server responding to requests. Unlike `TestClient` which mocks the outbound client, `TestServer` is the inbound side — it receives HTTP requests and returns responses.

**What is TestServer useful for?**

TestServer is ideal when:
- You're testing multiple routes working together
- Your routes depend on each other (e.g., creating a resource returns an ID you use in subsequent requests)
- You're testing middleware that applies to all routes
- You want integration tests that exercise the full request/response cycle without network I/O

TestServer binds to a port on localhost; while it uses real network I/O on localhost, this eliminates external network latency and disk I/O, making tests fast and deterministic. You make HTTP requests using the standard `Client` interface, which creates a realistic testing scenario.

**Basic server setup:**

The simplest TestServer test configures one or more routes, then uses a `Client` to make HTTP requests to the server:

```scala mdoc:passthrough
import utils._

printSource("zio-http-example-testing/src/test/scala/example/testing/GuideTestServerBasicSpec.scala")
```

([source](https://github.com/zio/zio-http/blob/main/zio-http-example-testing/src/test/scala/example/testing/GuideTestServerBasicSpec.scala))

The key difference from direct route testing: here we're actually making HTTP requests to the server via the `Client`, just like production code would. The `TestServer` receives those requests, matches them against configured routes, and returns responses.

**Testing multiple routes together:**

TestServer shines when testing how multiple routes work together:

```scala mdoc:passthrough
import utils._

printSource("zio-http-example-testing/src/test/scala/example/testing/GuideMultiRouteIntegrationSpec.scala")
```

([source](https://github.com/zio/zio-http/blob/main/zio-http-example-testing/src/test/scala/example/testing/GuideMultiRouteIntegrationSpec.scala))

Notice that routes are evaluated in order — the specific `GET /users/{id}` route is checked first, and if it doesn't match, the fallback `GET /...` route is checked. This mirrors production behavior and lets you test route precedence.

**Key differences from TestClient:**

- **TestClient** mocks the outbound `Client` — use when your code makes HTTP calls to external services
- **TestServer** mocks the inbound `Server` — use when testing code that receives HTTP requests
- **TestServer is more like production** — you make real HTTP requests that go through the full routing logic
- **TestServer is heavier** — it binds to a port and runs routing, so it's slower than direct route testing

## Testing Stateful Handlers

Many real-world handlers maintain state across requests. For example:
- An authentication handler tracks login attempts to prevent brute force attacks
- A rate limiting middleware tracks how many requests each user makes
- A caching handler caches values to avoid recomputing them
- A checkout handler maintains a shopping cart across multiple requests

When testing such handlers, you need to verify that state is correctly maintained as requests arrive.

**How to test state:**

Use a `Ref` (ZIO's mutable reference) to hold state that your handler can modify. The key is to make multiple requests and verify that the state changes correctly based on each request:

```scala mdoc:passthrough
import utils._

printSource("zio-http-example-testing/src/test/scala/example/testing/GuideStatefulHandlerSpec.scala")
```

([source](https://github.com/zio/zio-http/blob/main/zio-http-example-testing/src/test/scala/example/testing/GuideStatefulHandlerSpec.scala))

**Why is this important?**

Stateful handler testing reveals bugs that wouldn't be caught by testing a single request:
- State might not be properly reset between requests
- State updates might race (in concurrent scenarios)
- State might leak between different users or sessions
- Recovery logic might not work when state is corrupted

By making multiple requests in sequence and verifying the state changes at each step, you ensure your stateful logic is correct.

:::tip[Modeling Application State]
Use `Ref` for shared mutable state that handlers need to access. The test creates the `Ref` once, then handlers update it across multiple requests. This models how real applications maintain state — think of it as an in-memory database that all handlers have access to.
:::

## Testing WebSocket Connections

WebSockets are fundamentally different from HTTP: instead of request/response pairs, WebSocket connections are long-lived, bidirectional channels where either side can send messages at any time.

**How WebSocket testing works:**

Testing WebSockets is tricky because you need to test two sides of a conversation simultaneously:
1. The **server handler** — receives messages from clients and sends responses
2. The **client handler** — sends messages to the server and receives responses

ZIO HTTP provides `TestChannel` to make this work. A `TestChannel` is an in-memory, bidirectional message queue. You configure both a server handler and a client handler, then they communicate through the test channel — no actual network involved.

**Understanding the pairing model:**

When you call `client.socket(socketApp)`, the client sends a WebSocket upgrade request. TestClient intercepts this and:
1. Creates a `TestChannel` with two ends: one for the server, one for the client
2. Runs the server handler on one end
3. Runs the client app on the other end
4. Messages sent by the client appear in the server's input queue
5. Messages sent by the server appear in the client's input queue

**A simple echo server example:**

To create a WebSocket handler that echoes messages back to clients:

```scala mdoc:passthrough
import utils._

printSource("zio-http-example-testing/src/test/scala/example/testing/GuideWebSocketEchoSpec.scala")
```

([source](https://github.com/zio/zio-http/blob/main/zio-http-example-testing/src/test/scala/example/testing/GuideWebSocketEchoSpec.scala))

**Key concepts:**

- **`Handler.webSocket { channel => ... }`** — Creates a WebSocket handler that operates on a `WebSocketChannel`. The handler runs as a ZIO effect and can send and receive messages.
- **`channel.receive`** — Waits for the next message from the other side
- **`channel.receiveAll { case ... => ... }`** — Pattern matches on incoming messages and responds to each one
- **`channel.send(...)`** — Sends a message to the other side
- **`Read(WebSocketFrame.Text(...))`** — Wraps a text message in a `ChannelEvent.Read` so it can be sent/received

**The handshake:**

When you upgrade to WebSocket, both sides automatically receive a `HandshakeComplete` event as the first message. This signals that the upgrade succeeded and bidirectional communication can begin.

:::warning
WebSocket handlers run concurrently. Both the server and client handlers are running at the same time, each waiting to receive or send messages. Be careful about deadlocks — for example, if both sides wait to receive without sending first, they'll hang indefinitely.
:::

## Testing Error Scenarios and Edge Cases

A critical part of testing is verifying that your application handles errors correctly. This includes:
- **HTTP errors** — returning the right status codes (401, 404, 500, etc.)
- **Input validation** — rejecting invalid requests with helpful error messages
- **Fault tolerance** — handling timeouts, network errors, and other failures
- **State recovery** — cleaning up properly when things go wrong

**Why error testing matters:**

It's easy to test the "happy path" where everything works correctly. But in production, things fail — users send invalid data, external services time out, databases go down. Your code needs to handle these cases gracefully. Testing error scenarios ensures:
- Users get appropriate error messages (not a confusing 500 error)
- Error state doesn't leak to subsequent requests
- Security isn't compromised when validating input
- Failures are logged properly for debugging

**Testing HTTP status codes:**

Use direct route testing or `TestServer` to verify that your handlers return the correct status codes:

```scala mdoc:passthrough
import utils._

printSource("zio-http-example-testing/src/test/scala/example/testing/GuideErrorHandlingSpec.scala")
```

([source](https://github.com/zio/zio-http/blob/main/zio-http-example-testing/src/test/scala/example/testing/GuideErrorHandlingSpec.scala))

**Testing error messages:**

Beyond just the status code, verify that error messages are clear and helpful:

```scala mdoc:passthrough
import utils._

printSource("zio-http-example-testing/src/test/scala/example/testing/GuideValidationErrorSpec.scala")
```

([source](https://github.com/zio/zio-http/blob/main/zio-http-example-testing/src/test/scala/example/testing/GuideValidationErrorSpec.scala))

:::tip[Error Testing Pays Off]
Error testing is where the investment in testing really pays off. A single unhandled error path in production can impact thousands of users. By testing error scenarios systematically, you gain confidence that your application degrades gracefully under failure.
:::

## Choosing the Right Testing Approach

Now that you understand the three patterns, how do you decide which to use?

**Use direct route testing when:**
- You're unit testing a single handler or middleware in isolation
- You want the fastest possible feedback loop during development
- You don't need to test multiple routes or external dependencies
- Example: Testing a JSON parser, validation logic, or simple request transformation

**Use TestClient when:**
- Your code makes HTTP calls to external services (databases, APIs, message queues, etc.)
- You want to mock those dependencies and test failure scenarios
- You need to verify that your code makes the right HTTP calls with the right parameters
- Example: Testing a client that calls a payment processor, weather API, or recommendation service

**Use TestServer when:**
- You're testing multiple routes working together
- Your routes depend on each other (e.g., create → read → update → delete flows)
- You want to test middleware, authentication, or request routing logic
- You're doing integration testing of larger features
- Example: Testing a complete user management API with create, read, update, delete endpoints

**In practice, use all three:**

A well-tested application uses a mixture:
- **Unit tests** with direct route testing for individual handlers
- **Mock tests** with TestClient to verify integration with external services
- **Integration tests** with TestServer to test feature workflows

This pyramid approach gives you fast feedback from unit tests, confidence that external integrations work from mock tests, and assurance that features work end-to-end from integration tests.

## Best Practices for HTTP Testing

**1. Test both success and failure paths**

For every feature, write tests for:
- The happy path (everything works)
- Common failure cases (invalid input, missing data, etc.)
- Rare but serious failures (database unavailable, timeout, etc.)

**2. Test at the right level**

Don't test everything with TestServer. Use direct route testing for unit tests, reserve TestServer for features that truly need it. This keeps your test suite fast.

**3. Use meaningful test names**

Instead of `test("works")`, write `test("returns 401 when authorization header is missing")`. A good test name documents what you're testing.

**4. Test behavior, not implementation**

Test that the handler returns the right status code and response body. Don't test internal implementation details like "this variable was set correctly".

**5. Verify the whole request/response cycle**

Don't just check the status code. Verify:
- Status code is correct
- Response headers are correct
- Response body has the expected content
- Side effects happened (e.g., data was persisted)

This gives you confidence that the whole feature works, not just parts of it.

## Going Deeper

Once you're comfortable with the basics, consider these advanced testing techniques:

**Testing Middleware**

Middleware runs on every request. Test it by wrapping your routes with middleware and verifying that it transforms requests/responses correctly. For example, test that an authentication middleware rejects requests without credentials, or that a logging middleware doesn't interfere with response bodies.

**Testing Concurrent Behavior**

ZIO HTTP handlers can run concurrently. Use `TestServer` with multiple concurrent requests to test:
- Race conditions in stateful handlers
- Connection pooling behavior
- Concurrent request limits

**Integration Testing with Real Resources**

For end-to-end testing, combine TestServer with real resources:
- Real database connections (but use transactions to roll back)
- Real message queues (but use test instances)
- Real caches (but reset state between tests)

This gives you confidence that the whole system works together.

**Performance Testing**

Use TestClient to simulate load:
- Make thousands of requests and measure latency
- Test that handlers scale well under load
- Verify that resource cleanup happens correctly

**Error Recovery Testing**

Test that your handlers recover correctly from failures:
- Simulate database timeouts with slow test routes
- Test that connection pools recover after failures
- Verify that retries happen with correct backoff

## Summary

ZIO HTTP's functional model makes testing straightforward:
- **Direct route testing** for fast unit tests
- **TestClient** for mocking external dependencies
- **TestServer** for integration testing features
- All three can run without network I/O, making tests fast and reliable

The key insight is that routes are just functions — you can test them like any other ZIO effect, which makes HTTP testing simple and deterministic.

For more details, see:
- [ZIO Test documentation](https://zio.dev/reference/test/) — how to write ZIO tests
- [ZIO HTTP Handler reference](../reference/handler.md) — handler patterns and APIs
- [ZIO HTTP Route reference](../reference/routing/routes.md) — route definition and matching
