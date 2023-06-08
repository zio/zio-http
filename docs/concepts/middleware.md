---
id: middleware
title: Middleware
---

## Middleware

Middleware in ZIO-HTTP is a powerful mechanism that allows you to intercept and modify HTTP requests and responses. It provides a way to add additional functionality to an HTTP application in a modular and reusable manner.

Middleware functions are applied to an HTTP application to transform its behavior. Each middleware function takes an existing HTTP application as input and returns a new HTTP application with modified behavior. The composition of multiple middleware functions creates a pipeline through which requests and responses flow, allowing each middleware to perform specific actions.

Some common use cases for middleware include:

- Logging: Middleware can log request and response details, such as headers, paths, and payloads, for debugging or auditing purposes.

- Authentication and Authorization: Middleware can enforce authentication and authorization rules by inspecting request headers or tokens and validating user permissions.

- Error handling: Middleware can catch and handle errors that occur during request processing, allowing for centralized error handling and consistent error responses.

- Request preprocessing: Middleware can modify or enrich incoming requests before they are processed by the application. For example, parsing request parameters or validating input data.

- Response post-processing: Middleware can transform or enhance outgoing responses before they are sent back to the client. This includes adding headers, compressing data, or transforming response formats.

- Caching: Middleware can implement caching mechanisms to improve performance by storing and serving cached responses for certain requests.

- Rate limiting: Middleware can restrict the number of requests allowed from a client within a specific time frame to prevent abuse or ensure fair usage.

By composing multiple middleware functions together, you can build complex request processing pipelines tailored to your application's specific needs. Middleware promotes separation of concerns and code reusability by encapsulating different aspects of request handling in a modular way.

ZIO-HTTP provides a rich set of built-in middleware functions, and you can also create custom middleware by implementing the `HttpApp` or `HttpFilter` interfaces. This flexibility allows you to customize and extend the behavior of your HTTP applications in a declarative and composable manner.

here is a simple example of middleware:

```scala
package example

import java.util.concurrent.TimeUnit

import zio._

import zio.http._

object HelloWorldWithMiddlewares extends ZIOAppDefault {

  val app: HttpApp[Any, Nothing] = Http.collectZIO[Request] {
    // this will return result instantly
    case Method.GET -> Root / "text"         => ZIO.succeed(Response.text("Hello World!"))
    // this will return result after 5 seconds, so with 3 seconds timeout it will fail
    case Method.GET -> Root / "long-running" => ZIO.succeed(Response.text("Hello World!")).delay(5 seconds)
  }

  val serverTime: RequestHandlerMiddleware[Nothing, Any, Nothing, Any] = HttpAppMiddleware.patchZIO(_ =>
    for {
      currentMilliseconds <- Clock.currentTime(TimeUnit.MILLISECONDS)
      withHeader = Response.Patch.addHeader("X-Time", currentMilliseconds.toString)
    } yield withHeader,
  )
  val middlewares =
    // print debug info about request and response
    HttpAppMiddleware.debug ++
      // close connection if request takes more than 3 seconds
      HttpAppMiddleware.timeout(3 seconds) ++
      // add static header
      HttpAppMiddleware.addHeader("X-Environment", "Dev") ++
      // add dynamic header
      serverTime

  // Run it like any simple app
  val run = Server.serve((app @@ middlewares).withDefaultErrorResponse).provide(Server.default)
}
```

**Break down of the code**:
In the code above, the `middlewares` value is a composition of multiple middleware functions using the `++` operator. Each middleware function adds a specific behavior to the HTTP application (`app`) by modifying requests or responses.

The middleware functions used in the example are:

- `HttpAppMiddleware.debug`: This middleware logs debug information about the incoming requests and outgoing responses.

- `HttpAppMiddleware.timeout(3 seconds)`: This middleware closes the connection if the request takes more than 3 seconds, enforcing a timeout.

- `HttpAppMiddleware.addHeade("X-Environment", "Dev")`: This middleware adds a static header "X-Environment: Dev" to every response.

- `serverTime`: This middleware is a custom middleware that adds a dynamic header "X-Time" with the current timestamp to every response.

The composition of these middleware functions using the `++` operator creates a new HTTP application (`app @@ middlewares`) that incorporates all the defined middleware behaviors. The resulting application is then served by the server (`Server.serve`) with the addition of a default error response configuration.

By using middleware, you can modify the behavior of your HTTP application at various stages of the request-response cycle, such as request preprocessing, response post-processing, error handling, authentication, and more.
