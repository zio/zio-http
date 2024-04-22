---
id: routing
title: Routing
---

In ZIO HTTP, routes are a fundamental concept used to define how incoming HTTP requests are handled by an API. A route represents a specific combination of an HTTP method and a URL path pattern, along with a corresponding handler function.

Routes are organized into a collection known as a "routing table." The routing table acts as a mapping that decides where to direct each endpoint in the API based on the method and path of the incoming request. Each route in the routing table is associated with a handler function responsible for processing the request and generating an appropriate response.

To build a collection of routes, we can use the `Routes.apply` constructor or start with an empty route and gradually add routes to it. The process of defining routes is facilitated by the provided DSL, which allows us to construct route patterns using the `/` operator on the `Method` type.

Once we have built the routes, we can convert them into an HTTP application using the `toHttpApp` method. This transformation prepares the routes for execution by the ZIO HTTP server.

## Example

Here's an example of how we can define an endpoint using ZIO HTTP:

```scala mdoc:passthrough
import utils._

printSource("zio-http-example/src/main/scala/example/EndpointExamples.scala")
```

## Code Breakdown

In the given example above, the concepts of routing in ZIO HTTP are demonstrated using the ZIO and ZIO HTTP libraries. Let's go through the code to understand these concepts:

1. **Endpoint Definition**: Endpoints are defined using the `Endpoint` class, representing different routes of the API. Each endpoint is constructed using combinators like `Method.GET`, `int("userId")`, `query("name")`, etc. These combinators help define the HTTP method, path parameters, and query parameters, respectively.

2. **Middleware**: Middleware is added to the endpoints using the `@@` operator. In this case, the `auth` middleware is added to both `getUser` and `getUserPosts` endpoints using the `@@ auth` syntax.

3. **Implementation**: Each endpoint is implemented using the `implement` method. The implementation is provided as a `Handler` or `Handler.fromFunctionZIO`. The former is used when the implementation is a pure function, and the latter is used when the implementation is a ZIO effect.

4. **Routes**: The defined endpoints are collected into a `Routes` object using the `Routes` constructor. This creates a mapping of endpoint paths to their implementations.

5. **Server**: The `Routes` are converted into an `HttpApp` using the `toHttpApp` method. An `HttpApp` is a type alias for `Request => Task[Response]`, representing a function that takes an HTTP request and produces a ZIO task returning an HTTP response. The `Server.serve` method is used to run the `HttpApp` on a default server.

6. **ClientExample**: The `ClientExample` object demonstrates how to use the defined endpoints to make HTTP requests to the server. It shows how to create an `EndpointLocator` from the server URL and an `EndpointExecutor` to execute the requests.

ZIO HTTP allows us to define endpoints representing HTTP routes and then compose and implement those endpoints to create a complete API. By using middleware, we can add cross-cutting concerns like authentication and authorization to specific endpoints or to the entire API. The `HttpApp` is the final representation of the API, and it can be served on a server to handle incoming HTTP requests.