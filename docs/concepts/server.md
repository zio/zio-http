---
id: server
title: "Server"
---

# Server

Setting up an HTTP server is a fundamental aspect of building applications with ZIO HTTP. The server is responsible for listening to incoming requests, processing them, and sending back appropriate responses.

## Key Components and Concepts of ZIO Server:

### Server Creation:

To launch an HTTP server in ZIO HTTP, use the `Server.start` method. This method allows to specify the port number and the HTTP app to serve.

### Server Configuration

ZIO HTTP allows us to configure server according to various parameters such as host, port, leak detection level, request size and address. This ensures server operates optimally based on your specific requirements.

- **Host**: Define the host address on which the server will listen.
- **Port**: Set the port number for the server.
- **Leak Detection Level**: Configure the level of leak detection for debugging purposes.
- **Request Size**: Limit the maximum size of incoming requests to prevent resource exhaustion.
- **Address Customization**: Customize the server's address for specific deployment scenarios.

### Handling Requests:

- The server processes incoming HTTP requests and generates appropriate responses.
- Routes are defined using the `Http.collect` method, which maps requests to handlers.

### Integration with ZIO Ecosystem:

- The server integrates with the ZIO ecosystem, leveraging ZIO's concurrency and resource management capabilities.
- It can be configured to handle various aspects of HTTP communication, such as SSL/TLS for secure connections.

### Error Handling:

- ZIO HTTP provides mechanisms to handle errors that occur during request processing.
- Errors can be mapped to appropriate HTTP status codes and custom error messages.

### Middleware Support:

- Middleware can be applied to the server to handle cross-cutting concerns such as logging, authentication, and error handling.
- Middleware functions can be chained and composed to create complex behaviour.

## Simple Server Example

```scala mdoc:silent
import zio._
import zio.http._

val app: HttpApp[Any, Nothing] =
  Http.collect[Request] {
    case Method.GET -> !! / "hello" => Response.text("Hello, World!")
  }

val run = Server.start(8080, app)
```
In this example, we created a simple HTTP server that listens on port 8080 and responds with "Hello, World!" to GET requests on the `/hello` path.


