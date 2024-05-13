---
id: client
title: "Client"
---

# Client

ZIO HTTP empowers us to interact with remote HTTP servers by sending requests and handling responses. This enables us to build robust and scalable HTTP clients using ZIO's functional programming concepts.

## Key Components and Concepts of ZIO HTTP Client:

### Client Creation:

- Use `Client.request` (typically injected using dependency injection) to construct a ZIO effect representing the client interaction with the server.
- This method allows specifying the URL, additional headers, and other parameters.

### Request Building:

- ZIO HTTP provides methods for crafting HTTP requests:
  - Specify the HTTP method (GET, POST, PUT, DELETE, etc.)
  - Define the URL path and query parameters
  - Set request headers
  - Construct the request body (optional, depending on the request method)

### Response Handling:

- After sending a request, ZIO HTTP offers utilities for handling the server's response:
  - Extract the response body as a string using `response.body.asString`
  - Parse the response body using specific decoders (e.g., `body.asJson` for JSON)
  - Process the response body as a stream of bytes using `body.asStream`
  - Access response headers using `response.headers`
  - Check the response status code using `response.status`

### Client Configuration (Optional):

- Fine-tune the client's behavior with a `ZClient.Config` object:
  - Set request timeouts to prevent waiting indefinitely for a response
  - Control whether to follow redirects automatically
  - Configure SSL settings for secure communication

### Dependency Injection (Recommended):

- Leverage ZIO's dependency injection capabilities to provide necessary dependencies:
  - Client configuration (`ZClient.Config`)
  - Client implementation (often using `ZIO.service[Client]`)
  - Underlying network and DNS resolution dependencies

### Error Handling:

- ZIO HTTP employs comprehensive error handling mechanisms:
  - Errors like network failures, timeouts, or invalid responses are represented as typed errors.
  - Handle errors using ZIO combinators like `catchAll`, `orElse`, or `fold` to define appropriate behavior.

### Perfect Features of ZIO HTTP Client:

- **Purely Functional**: Aligns with ZIO principles, promoting referential transparency and composability.
- **Type Safety**: Utilizes Scala's type system to catch errors at compile time, enhancing code reliability.
- **Asynchronous & Non-blocking**: Enables concurrent HTTP requests without blocking threads, optimizing resource utilization.
- **Middleware Support**: Allows customization and extension through reusable middleware for logging, debugging, caching, authorization, etc.
- **Flexible Configuration**: Tailors client behavior with configuration options like timeouts, redirects, and SSL settings.
- **WebSocket Support**: Facilitates bidirectional communication for real-time or streaming data scenarios.
- **SSL/TLS Support**: Provides secure communication with built-in SSL/TLS support.
- **Integration with ZIO Ecosystem**: Works seamlessly with other ZIO modules for a cohesive functional approach.


## Making HTTP Requests

Once you have an HTTP client, you can use it to make various types of requests (GET, POST, PUT, DELETE, etc.) to external services. ZIO HTTP provides methods for constructing and sending requests.

```scala mdoc:silent 

import zio.http._
import zio._

val url = URL.decode("https://api.example.com/data")

val request_default = Request.get(url) 
```
In this example,created a simple GET request to the `https://api.example.com/data` endpoint and send it using the HTTP client. The send method returns a Response representing the server's response to the request.

## Handling Responses:

After sending a request, you can handle the response returned by the server. ZIO HTTP provides various methods for processing response data, such as reading headers, accessing the body and handling status codes.

```scala mdoc:silent 
import utils._

printSource("zio-http-example/src/main/scala/example/ClientWithDecompression.scala")
```

In this example, a GET request sends to the `https://jsonplaceholder.typicode.com`endpoint and extract the response body as a string using the `body.asString` method.

**Simple Client Example**

```scala mdoc:passthrough
import utils._

printSource("zio-http-example/src/main/scala/example/SimpleClient.scala")

```

This example demonstrates how to create a simple HTTP client using ZIO HTTP to make requests to an external API. It sends a request to the specified URL with custom headers, retrieves the response body as a string and prints it to the console.









