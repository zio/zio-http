---
id: examples
title: "Running the Examples"
---

All code examples from this testkit guide are available as runnable tests in the `zio-http-example-testing` project. Each example demonstrates a specific testing pattern and can be compiled and run independently.

## Setup

Clone the repository and navigate to the project:

```bash
git clone https://github.com/zio/zio-http.git
cd zio-http
```

Next, navigate to the examples project:

```bash
cd zio-http-example-testing
```

Then, compile all examples:

```bash
sbt test:compile
```

## Example Test Suites

The testkit provides example suites demonstrating each testing pattern.

### Direct Route Testing

To run the direct route testing examples:

```bash
sbt "testOnly example.testing.DirectRouteTestingExamples"
```

**Key patterns covered:**
- Simple handler responses (OK status, text responses)
- Routing and path matching with specific vs. fallback routes
- Extracting path parameters (integers and strings)
- Different HTTP methods (GET, POST, etc.)
- Reading and echoing request body

### TestServer Integration Testing

To run the TestServer integration testing examples:

```bash
sbt "testOnly example.testing.TestServerExamples"
```

**Key patterns covered:**
- Basic server setup and route configuration
- Adding single and multiple routes
- Route precedence (specific routes before general fallback routes)
- Extracting path parameters in routes
- Different HTTP methods on the same path
- Server returning correct status codes
- Handling unmatched routes (404 responses)
- Request body handling and error responses
- Integration testing with request-response workflows
- State persistence across multiple requests

### TestClient Mocking

To run the TestClient mocking examples:

```bash
sbt "testOnly example.testing.TestClientExamples"
```

**Key patterns covered:**
- Mock exact request-response pairs
- Mock multiple different endpoints
- Mock different HTTP methods
- Flexible route handlers that respond to parameters
- Handlers that compute responses from request content
- Fallback handlers for capturing unexpected requests
- Verifying no extra calls are made
- Route accumulation (routes persist across multiple additions)
- Mocking error responses from external services
- Mocking authentication failures

### WebSocket Testing

To run the WebSocket testing examples:

```bash
sbt "testOnly example.testing.WebSocketExamples"
```

**Key patterns covered:**
- Echo servers that echo messages back to clients
- Bidirectional message exchange (client sends, server responds)
- Servers that send unsolicited messages (greetings)
- Handling different frame types (text and binary)
- Stateful WebSocket handlers (counters, session data)
- Server-initiated shutdown and connection cleanup
- Broadcast pattern demonstration

### Error Handling Patterns

To run the error handling examples:

```bash
sbt "testOnly example.testing.ErrorHandlingExamples"
```

**Key patterns covered:**
- Returns 404 for missing resources
- Returns error status codes (400, 500, etc.)
- Adds custom error headers
- Validates request data
- Returns appropriate error responses with messages

### Stateful Handlers

To run the stateful handler examples:

```bash
sbt "testOnly example.testing.StatefulHandlerExamples"
```

**Key patterns covered:**
- Counter handlers that increment on each request
- State persistence across multiple requests
- Request tracking and counting
- Using Ref for mutable state in handlers

## Running All Tests

To run all testkit examples at once:

```bash
sbt "testOnly example.testing.*"
```

Or compile the entire test suite:

```bash
sbt test:compile
```

## Project Structure

The examples follow ZIO HTTP testing conventions:

```
zio-http-example-testing/
├── src/
│   ├── test/scala/example/testing/
│   │   ├── DirectRouteTestingExamples.scala
│   │   ├── TestServerExamples.scala
│   │   ├── TestClientExamples.scala
│   │   ├── WebSocketExamples.scala
│   │   ├── ErrorHandlingExamples.scala
│   │   └── StatefulHandlerExamples.scala
│   └── main/scala/example/
│       └── (application code if needed)
└── build.sbt
```

Each example file is a standalone test suite that can be run independently using `sbt "testOnly"` commands.

## Integration with Documentation

These examples directly correspond to sections in the testkit reference:

| Example File | Reference | Topics |
|---|---|---|
| `DirectRouteTestingExamples.scala` | [Direct Route Testing](../../guides/testing-http-apps) | Unit testing, handler logic, routing |
| `TestServerExamples.scala` | [TestServer](./test-server.md) | Integration testing, multiple routes, state |
| `TestClientExamples.scala` | [TestClient](./test-client.md) | Mocking, external services, fallback handling |
| `WebSocketExamples.scala` | [TestChannel](./test-channel.md) | WebSocket, bidirectional messaging, frames |
| `ErrorHandlingExamples.scala` | [Error Handling](../../guides/testing-http-apps) | Status codes, error responses |
| `StatefulHandlerExamples.scala` | [State Persistence](../../guides/testing-http-apps) | Ref, state management, request tracking |

## Writing Your Own Tests

To create your own tests following these patterns:

1. **Extend ZIOSpecDefault** for test suite structure
2. **Use TestServer.default, TestClient.layer, or other providers** as needed
3. **Follow naming conventions** with descriptive suite and test names
4. **Compose handlers** to test multiple routes together
5. **Use Ref for state** to persist state across requests
6. **Handle errors gracefully** with `catchAll` patterns

Refer to the individual example files and the [TestServer](./test-server.md), [TestClient](./test-client.md), and [TestChannel](./test-channel.md) reference pages for complete patterns and best practices.
