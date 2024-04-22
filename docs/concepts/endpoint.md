---
id: endpoint
title: Endpoint
---

Endpoints in ZIO HTTP are defined using the `Endpoint` object's combinators, which provide a type-safe way to specify various aspects of the endpoint. For instance, consider defining endpoints for retrieving user information and user posts:

```scala mdoc:silent
import zio._
import zio.http._
import zio.http.endpoint.{Endpoint, EndpointExecutor, EndpointLocator, EndpointMiddleware}
import zio.http.codec.{HttpCodec, PathCodec}
import HttpCodec.query

val auth = EndpointMiddleware.auth

val getUser = Endpoint(Method.GET / "users" / int("userId")).out[Int]

val getUserPosts = Endpoint(Method.GET / "users" / int("userId") / "posts" / int("postId"))
  .query(query("name"))
  .out[List[String]] @@ auth
```

In these examples, we use combinators like `Method.GET`, `int`, and `query` to define the HTTP method, URL path, path parameters, query parameters, and response types of the endpoints.

## Middleware Application

Middleware can be applied to endpoints using the `@@` operator to add additional behavior or processing. For example, we can apply authentication middleware to restrict access to certain endpoints:

```scala mdoc:silent

val getUserRoute =
  getUser.implement {
    Handler.fromFunction[Int] { id =>
      id
    }
  }
```

Here, the `auth` middleware ensu authenticated users can access the `getUser` endpoint.

## Endpoint Implementation

Endpoints are implemented using the `implement` method, which takes a function specifying the logic to handle the request and generate the response. Inside the implementation function, ZIO effects can be used to perform computations and interact with dependencies:

```scala mdoc:fail
val getUserRoute =
  getUser.implement {
    Handler.fromFunction[Int] { id =>
      id
    }
  }
```

In this example, the implementation function takes an `Int` representing the user ID and returns a ZIO effect that produces the same ID.

## Endpoint Composition

Endpoints can be composed together using operators like `++`, allowing us to build a collection of endpoints that make up our API:

```scala mdoc:silent

val getUserPostsRoute =
    getUserPosts.implement[Any] {
      Handler.fromFunctionZIO[(Int, Int, String)] { case (id1: Int, id2: Int, query: String) =>
        ZIO.succeed(List(s"API2 RESULT parsed: users/$id1/posts/$id2?name=$query"))
      }
    }

val routes = Routes(getUserRoute, getUserPostsRoute)
```

Here, we compose the `getUserRoute` and `getUserPostsRoute` endpoints into a collection of routes.

## Converting to App

To serve the defined endpoints, they need to be converted to an HTTP application (`HttpApp`). This conversion is done using the `toHttpApp` method:

```scala mdoc:silent
 val app = routes.toHttpApp
```

Any required middleware can be applied during this conversion to the final app, ensuring that the specified behavior is enforced for each incoming request.

## Running an App

The ZIO HTTP server requires an `HttpApp[R]` to run. The server can be started using the `Server.serve()` method, which takes the HTTP application as input and any necessary configurations:

```scala 
val run = Server.serve(app).provide(Server.default)
```

The server listens on the specified port, accepts incoming connections, and routes the incoming HTTP requests to the appropriate endpoints.

## Purposes and Benefits of Endpoints in ZIO HTTP:

### Purpose:
- **Type-Safe Endpoint Definition:** Endpoints in ZIO HTTP are defined using combinators, ensuring type safety and preventing runtime errors related to endpoint configuration.
- **Clear API Specification:** The use of combinators allows for a clear and concise specification of endpoints, including HTTP method, URL path, path parameters, query parameters, and response types.

### Benefits:
- **Enhanced Readability:** Endpoint definitions using combinators improve code readability by providing a declarative way to describe API endpoints.
- **Improved Maintainability:** The type-safe nature of endpoint definitions reduces the likelihood of errors and facilitates maintenance by making it easier to understand and modify endpoints.
- **Simplified Middleware Application:** Middleware can be applied directly to endpoints, enabling easy addition of cross-cutting concerns such as authentication, logging, or validation.
- **Flexible Endpoint Composition:** Endpoints can be composed together using operators like `++`, allowing for the creation of complex APIs from simpler endpoint definitions.

### Why Use Endpoints in ZIO HTTP:
- **Type Safety:** Endpoints offer strong compile-time guarantees, reducing the risk of runtime errors and enhancing code robustness.
- **Expressiveness:** The combinators provided by ZIO HTTP allow for expressive and concise endpoint definitions, improving developer productivity and code readability.
- **Integration with ZIO Ecosystem:** Endpoints seamlessly integrate with the ZIO ecosystem, enabling the use of ZIO effects for handling endpoint logic and dependencies.

### Benefit of Separating Endpoint Definition from Implementation:
- **Modularity:** Separating the definition of endpoints from their implementation promotes modularity and separation of concerns, making it easier to reason about and maintain the codebase.
- **Testability:** By decoupling endpoint definition from implementation, each component can be tested independently, facilitating unit testing and ensuring code quality.
- **Flexibility:** Changes to the implementation of an endpoint can be made without affecting its definition, providing flexibility and allowing for iterative development and refactoring.

The concept of endpoints in ZIO HTTP provides a powerful and type-safe way to define, implement, and serve API operations. By leveraging combinators, middleware, and composition, developers can create robust and scalable API services with ease. [Full code Implementation](https://github.com/zio/zio-http/blob/main/zio-http-example/src/main/scala/example/EndpointExamples.scala) For more in-depth details, check out [Reference](reference/dsl/endpoint)