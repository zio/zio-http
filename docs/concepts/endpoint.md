---
id: endpoint
title: Endpoint
---

Endpoints in ZIO HTTP are defined using the `Endpoint` object's combinators, which provide a type-safe way to specify various aspects of the endpoint. For instance, consider defining endpoints for retrieving user information and user posts:

```scala mdoc:invisible
val getUser = Endpoint(Method.GET / "users" / int("userId")).out[Int]

val getUserPosts = Endpoint(Method.GET / "users" / int("userId") / "posts" / int("postId"))
  .query(query("name"))
  .out[List[String]]
```

In these examples, we use combinators like `Method.GET`, `int`, and `query` to define the HTTP method, URL path, path parameters, query parameters, and response types of the endpoints.

### Middleware Application

Middleware can be applied to endpoints using the `@@` operator to add additional behavior or processing. For example, we can apply authentication middleware to restrict access to certain endpoints:

```scala mdoc:invisible
val auth = EndpointMiddleware.auth

val getUserRoute = getUser.implement {
  Handler.fromFunction[Int] { id =>
    id
  }
} @@ auth
```

Here, the `auth` middleware ensures that only authenticated users can access the `getUser` endpoint.

### Endpoint Implementation

Endpoints are implemented using the `implement` method, which takes a function specifying the logic to handle the request and generate the response. Inside the implementation function, ZIO effects can be used to perform computations and interact with dependencies:

```scala mdoc:invisible
val getUserRoute = getUser.implement[Any] {
  Handler.fromFunctionZIO[Int] { id =>
    ZIO.succeed(id)
  }
}
```

In this example, the implementation function takes an `Int` representing the user ID and returns a ZIO effect that produces the same ID.

### Endpoint Composition

Endpoints can be composed together using operators like `++`, allowing us to build a collection of endpoints that make up our API:

```scala mdoc:invisible
val routes = Routes(getUserRoute, getUserPostsRoute)
```

Here, we compose the `getUserRoute` and `getUserPostsRoute` endpoints into a collection of routes.

### Converting to App

To serve the defined endpoints, they need to be converted to an HTTP application (`HttpApp`). This conversion is done using the `toHttpApp` method:

```scala mdoc:invisible
 val app = routes.toHttpApp
```

Any required middleware can be applied during this conversion to the final app, ensuring that the specified behavior is enforced for each incoming request.

### Running an App

The ZIO HTTP server requires an `HttpApp[R]` to run. The server can be started using the `Server.serve()` method, which takes the HTTP application as input and any necessary configurations:

```scala mdoc:invisible
val run = Server.serve(app).provide(Server.default)
```

The server listens on the specified port, accepts incoming connections, and routes the incoming HTTP requests to the appropriate endpoints.

The concept of endpoints in ZIO HTTP provides a powerful and type-safe way to define, implement, and serve API operations. By leveraging combinators, middleware, and composition, developers can create robust and scalable API services with ease. [Full code Implementation](https://github.com/zio/zio-http/blob/main/zio-http-example/src/main/scala/example/EndpointExamples.scala)