---
id: request-handling
title: Request Handling
---

## Request Handling

In ZIO-HTTP, request handling is the process of receiving an HTTP request, processing it, and generating an appropriate HTTP response. ZIO-HTTP provides a functional and composable approach to request handling using the HttpApp type.

The HttpApp type represents an HTTP application, which is essentially a function that takes an incoming Request and returns a Response. It is defined as type `HttpApp[R, E] = Request => ZIO[R, E, Response[E]]`, where R represents the required environment, E represents the potential error type, and Response[E] represents the response type.

Here's an overview of the request handling process in ZIO-HTTP:

- Defining an HttpApp: To handle requests, you define one or more HttpApp values. Each HttpApp is responsible for processing a specific type of request or a specific path pattern.

- Request Matching: The Http.collect or Http.collectM combinator is used to pattern match on the incoming Request. It allows you to define matching criteria based on HTTP method, path, headers, etc.

- Request Processing: Once a matching rule is found, you can perform any required processing on the request. This can include extracting information from the request, validating inputs, invoking other services, or performing any necessary business logic. The processing typically results in the creation of a Response.

- Generating the Response: The processing logic within the HttpApp should construct an appropriate Response based on the request and any additional computation. The Response type encapsulates the HTTP status, headers, and body.

- Error Handling: During the request handling process, errors may occur. ZIO-HTTP allows you to handle and propagate errors in a type-safe manner using ZIO's ZIO[R, E, A] data type. You can use various combinators and operators provided by ZIO to handle errors, perform error recovery, or propagate errors to higher layers.


- Composing HttpApp Instances: ZIO-HTTP provides combinators to compose multiple HttpApp instances together. This allows you to build complex routing logic by combining multiple handlers for different paths or methods. Combinators like @@, ||, &&, and orElse can be used to combine, match, and route requests to the appropriate HttpApp instances.

- Server Configuration: Once you have defined your HttpApp or a composition of HttpApp instances, you can configure the server settings, such as the port to listen on, TLS settings, or other server-specific options.

- Running the Server: To start the server and begin handling requests, you use the Server.serve method, providing your HttpApp as the main application to be served. You can also provide additional server-specific configurations if needed.

Here's an example of request handling:

```scala
package example

import zio._

import zio.http._

object RequestStreaming extends ZIOAppDefault {

  // Create HTTP route which echos back the request body
  val app = Http.collect[Request] { case req @ Method.POST -> Root / "echo" =>
    // Returns a stream of bytes from the request
    // The stream supports back-pressure
    val stream = req.body.asStream

    // Creating HttpData from the stream
    // This works for file of any size
    val data = Body.fromStream(stream)

    Response(body = data)
  }

  // Run it like any simple app
  val run: UIO[ExitCode] =
    Server.serve(app).provide(Server.default).exitCode
}
```

**Explainaition**:

- `app` Definition: The `app` value represents an HttpApp that handles incoming requests. It is defined using the `Http.collect` combinator, which pattern matches on the incoming Request. In this case, it matches a POST request to the `/echo` endpoint.

- Request Matching: The `Http.collect` combinator pattern matches on the request using a partial function. It checks if the request's method is     `POST` and if the request path is `/echo`.

- Request Processing: Once a matching rule is found, the code inside the `Http.collect` block is executed. In this case, it retrieves the request body as a stream of bytes using `req.body.asStream`.

- Creating `HttpData`: The code then creates an HttpData instance from the request stream using `Body.fromStream(stream)`. `HttpData` represents the body of an HTTP response and can be created from various sources, such as streams, byte arrays, or strings.

- Generating the Response: Finally, the code constructs a `Response` with the created `HttpData` as the body. The response will echo back the received request body.

- Running the Server: The `run` value is responsible for starting the server and handling incoming requests. It uses the `Server.serve` method to serve the app as the main application. The server is provided with the default server configuration using `Server.default`. The `exitCode` method is used to provide an appropriate exit code for the application.


Overall, the concept of request handling in ZIO-HTTP revolves around defining HttpApp instances, matching incoming requests, processing them, generating responses, and composing multiple HttpApp instances to build a complete HTTP server application. The functional and composable nature of ZIO allows for a flexible and modular approach to building robust and scalable HTTP services.
