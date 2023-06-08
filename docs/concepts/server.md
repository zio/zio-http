---
id: server
title: Server
---

## Server

The concept of a server in ZIO-HTTP revolves around handling incoming HTTP requests and producing corresponding HTTP responses. The server is responsible for listening on a specific port, accepting incoming connections, and routing requests to appropriate handlers based on their HTTP method and path.

ZIO-HTTP provides a simple and composable DSL for defining HTTP servers using the `Http` type. The `Http` type represents an HTTP route or endpoint that can handle incoming requests and produce responses. Servers in ZIO-HTTP are created by defining an Http route and then using the `Server.serve` method to start the server and bind it to a specific port.

Here are the key components involved in the server concept in ZIO-HTTP:

- HTTP Route: A route is defined using the `Http.collect` method, which takes a partial function mapping requests to their corresponding responses. The partial function matches on the HTTP method and path of the request and returns a `Response` for each matched request.

- Server Configuration: The server configuration includes information such as the host, port, SSL settings, and other options. In ZIO-HTTP, the server configuration is provided through the `ServerConfig` type, which can be customized as needed.

- Starting the Server: The `Server.serve` method is used to start the server by providing it with the HTTP route and the server configuration. This method creates a ZIO effect that represents the running server. The server will listen for incoming connections and route the requests to the appropriate handlers defined in the route.

- Server Environment: ZIO-HTTP leverages the ZIO library, which uses an environment-based approach for dependency injection. The server environment consists of the necessary services and resources required for the server to operate, such as the event loop, clock, console, and any other dependencies needed by the defined routes.

By combining these components, ZIO-HTTP allows you to define and run servers that handle incoming HTTP requests and produce HTTP responses. The server concept in ZIO-HTTP emphasizes composability, type-safety, and a functional programming approach, making it easier to build robust and scalable HTTP servers in a purely functional manner.

Here is an example:

```scala
package example

import java.time.LocalDateTime
import java.time.format.DateTimeFormatter.ISO_LOCAL_TIME

import zio.{ExitCode, Schedule, URIO, ZIOAppDefault, durationInt}

import zio.stream.ZStream

import zio.http._

object SSEServer extends ZIOAppDefault {

  val stream: ZStream[Any, Nothing, ServerSentEvent] =
    ZStream.repeatWithSchedule(ServerSentEvent(ISO_LOCAL_TIME.format(LocalDateTime.now)), Schedule.spaced(1.second))

  val app: Http[Any, Nothing, Request, Response] = Http.collect[Request] { case Method.GET -> Root / "sse" =>
    Response.fromServerSentEvents(stream)
  }

  val run: URIO[Any, ExitCode] = {
    Server.serve(app.withDefaultErrorResponse).provide(Server.default).exitCode
  }
}
```

**Explaination:**

- `stream`: It defines a ZStream that produces `ServerSentEvent` values. In this example, it repeats the current time as an SSE every 1 second using ZIO's scheduling capabilities.

- `app`: It creates an `Http` route using `Http.collect`. The route matches a GET request to the path "/sse". When a request matches this route, it responds with an SSE stream created from the `stream` defined earlier using Response.`fromServerSentEvents`.

- `run`: It starts the HTTP server by invoking `Server.serve` with the `app` as the HTTP application and `Server.default` as the server configuration. It then uses ZIO's provide method to provide the necessary environment for the server to run. Finally, it obtains the `ExitCode` from the server execution.

Overall, this code demonstrates how to define a server using ZIO-HTTP's `Http` DSL, create an HTTP route for an SSE endpoint, and start the server to listen for incoming connections and serve the SSE stream to clients.