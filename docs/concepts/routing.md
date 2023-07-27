---
id: routing
title: Routing
---

# Routing 

In ZIO-HTTP, routes are a fundamental concept used to define how incoming HTTP requests are handled by an API. A route represents a specific combination of an HTTP method and a URL path pattern, along with a corresponding handler function.

Routes are organized into a collection known as a "routing table." The routing table acts as a mapping that decides where to direct each endpoint in the API based on the method and path of the incoming request. Each route in the routing table is associated with a handler function responsible for processing the request and generating an appropriate response.

To build a collection of routes, you can use the Routes.apply constructor or start with an empty route and gradually add routes to it. The process of defining routes is facilitated by the provided DSL, which allows you to construct route patterns using the `/` operator on the `Method` type.

Once you have built the routes, you can convert them into an HTTP application using the `toHttpApp` method. This transformation prepares the routes for execution by the ZIO HTTP server.


Here's an example of how you can define an endpoint using ZIO-HTTP:

```scala
package example

import zio._

import zio.http.Header.Authorization
import zio.http.codec.{HttpCodec, PathCodec}
import zio.http.endpoint._
import zio.http.{int => _, _}

object EndpointExamples extends ZIOAppDefault {
  import HttpCodec._
  import PathCodec._

  val auth = EndpointMiddleware.auth

  // MiddlewareSpec can be added at the service level as well
  val getUser =
    Endpoint(Method.GET / "users" / int("userId")).out[Int] @@ auth

  val getUserRoute =
    getUser.implement {
      Handler.fromFunction[Int] { id =>
        id
      }
    }

  val getUserPosts =
    Endpoint(Method.GET / "users" / int("userId") / "posts" / int("postId"))
      .query(query("name"))
      .out[List[String]] @@ auth

  val getUserPostsRoute =
    getUserPosts.implement[Any] {
      Handler.fromFunctionZIO[(Int, Int, String)] { case (id1: Int, id2: Int, query: String) =>
        ZIO.succeed(List(s"API2 RESULT parsed: users/$id1/posts/$id2?name=$query"))
      }
    }

  val routes = Routes(getUserRoute, getUserPostsRoute)

  val app = routes.toHttpApp // (auth.implement(_ => ZIO.unit)(_ => ZIO.unit))

  val request = Request.get(url = URL.decode("/users/1").toOption.get)

  val run = Server.serve(app).provide(Server.default)

  object ClientExample {
    def example(client: Client) = {
      val locator =
        EndpointLocator.fromURL(URL.decode("http://localhost:8080").toOption.get)

      val executor: EndpointExecutor[Authorization] =
        EndpointExecutor(client, locator, ZIO.succeed(Authorization.Basic("user", "pass")))

      val x1 = getUser(42)
      val x2 = getUserPosts(42, 200, "adam")

      val result1: ZIO[Scope, Nothing, Int]          = executor(x1)
      val result2: ZIO[Scope, Nothing, List[String]] = executor(x2)

      result1.zip(result2).debug
    }
  }
}
```

In the given example above, the concepts of routing in ZIO-HTTP are demonstrated using the ZIO and zio-http libraries. Let's go through the code to understand these concepts:

1. **Endpoint Definition**: Endpoints are defined using the `Endpoint` class, representing different routes of the API. Each endpoint is constructed using combinators like `Method.GET`, `int("userId")`, `query("name")`, etc. These combinators help define the HTTP method, path parameters, and query parameters, respectively.

2. **Middleware**: Middleware is added to the endpoints using the `@@` operator. In this case, the `auth` middleware is added to both `getUser` and `getUserPosts` endpoints using the `@@ auth` syntax.

3. **Implementation**: Each endpoint is implemented using the `implement` method. The implementation is provided as a `Handler` or `Handler.fromFunctionZIO`. The former is used when the implementation is a pure function, and the latter is used when the implementation is a ZIO effect.

4. **Routes**: The defined endpoints are collected into a `Routes` object using the `Routes` constructor. This creates a mapping of endpoint paths to their implementations.

5. **Server**: The `Routes` are converted into an `HttpApp` using the `toHttpApp` method. An `HttpApp` is a type alias for `Request => Task[Response]`, representing a function that takes an HTTP request and produces a ZIO task returning an HTTP response. The `Server.serve` method is used to run the `HttpApp` on a default server.

6. **ClientExample**: The `ClientExample` object demonstrates how to use the defined endpoints to make HTTP requests to the server. It shows how to create an `EndpointLocator` from the server URL and an `EndpointExecutor` to execute the requests.

ZIO-HTTP allows you to define endpoints representing HTTP routes and then compose and implement those endpoints to create a complete API. By using middleware, you can add cross-cutting concerns like authentication and authorization to specific endpoints or to the entire API. The `HttpApp` is the final representation of the API, and it can be served on a server to handle incoming HTTP requests.