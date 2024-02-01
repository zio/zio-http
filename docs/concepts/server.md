---
id: server
title: Server
---

The concept of a server in ZIO HTTP revolves around handling incoming HTTP requests and producing corresponding HTTP responses. The server is responsible for listening on a specific port, accepting incoming connections, and routing requests to appropriate handlers based on their HTTP method and path.

ZIO HTTP provides a simple and composable DSL for defining HTTP servers using the `Http` type. The `Http` type represents an HTTP route or endpoint that can handle incoming requests and produce responses. Servers in ZIO HTTP are created by defining an HTTP route and then using the `Server.serve` method to start the server and bind it to a specific port.

Here are the key components involved in the server concept in ZIO-HTTP:

1. **HTTP Route**:
   - A route is defined using the `Http.collect` method, which takes a partial function mapping requests to their corresponding responses.
   - The partial function matches on the HTTP method and path of the request and returns a `Response` for each matched request.
   - This allows developers to define multiple routes and their corresponding handlers to handle different types of requests.

2. **Server Configuration**:
   - The server configuration includes information such as the host, port, SSL settings, and other options.
   - In ZIO HTTP, the server configuration is provided through the `ServerConfig` type, which can be customized as needed.
   - This allows developers to configure the server to meet specific requirements, such as enabling HTTPS or adjusting thread pool sizes.

3. **Starting the Server**:
   - The `Server.serve` method is used to start the server by providing it with the HTTP route and the server configuration.
   - This method creates a ZIO effect that represents the running server. The server will listen for incoming connections and route the requests to the appropriate handlers defined in the route.
   - The server is started asynchronously and can run indefinitely until explicitly shut down.

4. **Server Environment**:
   - ZIO HTTP leverages the ZIO library, which uses an environment-based approach for dependency injection.
   - The server environment consists of the necessary services and resources required for the server to operate, such as the event loop, clock, console, and any other dependencies needed by the defined routes.
   - This ensures that the server has access to all the required resources and allows for easy testing and mocking of dependencies.

By combining these components, ZIO HTTP allows you to define and run servers that handle incoming HTTP requests and produce HTTP responses. The server concept in ZIO HTTP emphasizes composability, type-safety, and a functional programming approach, making it easier to build robust and scalable HTTP servers in a purely functional manner. The flexible and composable nature of ZIO HTTP enables developers to create sophisticated and high-performance servers with ease.

## Example

Let's explore a Scala code snippet featuring a ZIO-based Server-Sent Events (SSE) server that continuously sends timestamped events to clients at one-second intervals:

```scala mdoc:passthrough
import utils._

printSource("zio-http-example/src/main/scala/example/SSEServer.scala")
```

## Code Breakdown

- `stream`: It defines a ZStream that produces `ServerSentEvent` values. In this example, it repeats the current time as an SSE every 1 second using ZIO's scheduling capabilities.

- `app`: It creates an `Http` route using `Http.collect`. The route matches a GET request to the path "/sse". When a request matches this route, it responds with an SSE stream created from the `stream` defined earlier using Response.`fromServerSentEvents`.

- `run`: It starts the HTTP server by invoking `Server.serve` with the `app` as the HTTP application and `Server.default` as the server configuration. It then uses ZIO's provide method to provide the necessary environment for the server to run. Finally, it obtains the `ExitCode` from the server execution.

Overall, this code demonstrates how to define a server using ZIO HTTP's `Http` DSL, create an HTTP route for an SSE endpoint, and start the server to listen for incoming connections and serve the SSE stream to clients.

## Configuring ZIO HTTP Server

This section describes, ZIO HTTP Server and different configurations you can provide while creating the Server

### Default Configurations

Assume we have an `HttpApp`:

```scala mdoc:silent
import zio.http._
import zio._

def app: HttpApp[Any] = ???
```

We can start a ZIO HTTP Server with default configurations using `Server.serve`:

```scala mdoc:compile-only
Server.serve(app).provide(Server.default)
```

A quick shortcut to only customize the port is `Server.defaultWithPort`:

```scala mdoc:compile-only
Server.serve(app).provide(Server.defaultWithPort(8081))
```

Or to customize more properties of the _default configuration_:

```scala mdoc:compile-only
Server.serve(app).provide(
  Server.defaultWith(
    _.port(8081).enableRequestStreaming
  )
)
```

### Custom Configurations

The `live` layer expects a `Server.Config` holding the custom configuration for the server.

```scala mdoc:silent:crash
Server
  .serve(app)
  .provide(
    ZLayer.succeed(Server.Config.default.port(8081)),
    Server.live
  )
```

The `configured` layer loads the server configuration using the application's _ZIO configuration provider_, which
is using the environment by default but can be attached to a different backends using
the [ZIO Config library](https://zio.github.io/zio-config/).

```scala mdoc:silent:crash
Server
  .serve(app)
  .provide(
    Server.configured()
  )
```

In order to customize Netty-specific properties, the `customized` layer can be used, providing not only `Server.Config`
but also `NettyConfig`.
