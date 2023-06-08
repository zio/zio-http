---
id: endpoint
title: Endpoint
---

## Endpoint

Endpoints in ZIO-HTTP represent individual API operations or routes that the server can handle. They define the structure and behavior of the API endpoints in a type-safe manner. Let's break down the key aspects:

- Endpoint Definition:
  - Endpoints are defined using the `Endpoint` object's combinators, such as `get`, `post`, `path`, `query`, and more.
  
  - Combinators allow you to specify the HTTP method, URL path, query parameters, request/response bodies, and other details of the endpoint.

- Middleware:
  - Middleware can be applied to endpoints using the `@@` operator to add additional behavior or processing to the endpoint.

  - Middleware can handle authentication, validation, error handling, logging, or any custom logic needed for the endpoint.

- Endpoint Implementation:
  - Endpoints are implemented using the `implement` method, which takes a function specifying the logic to handle the request and generate the response.

  - Inside the implementation function, you can use ZIO effects to perform computations, interact with dependencies, and produce the desired response.

- Endpoint Composition:
  - Endpoints can be composed together using operators like `++`, allowing you to build a collection of endpoints that make up your API.

  - Composition enables structuring the API by grouping related endpoints or creating reusable components.

- Converting to App:
  - To serve the defined endpoints, they need to be converted to an HTTP application (`HttpApp`).

  - This conversion is done using the `toApp` method, which prepares the endpoints to be served as an HTTP application.

  - Any required middleware can be applied during this conversion to the final app.

- Server:
  - The server is responsible for starting the HTTP server and making the app available to handle incoming requests.

  - The `Server.serve` method is used to start the server by providing the HTTP application and any necessary configurations.

- Client Interaction:
  - ZIO-HTTP also provides facilities to interact with endpoints as a client.

  - An `EndpointExecutor` can be created to execute the defined endpoints on the client-side, providing input values and handling the response.

Overall, endpoints in ZIO-HTTP define the structure, behavior, and implementation of individual API operations. They enable type-safe routing, request/response handling, and composition of API routes. Endpoints can be converted into an HTTP application and served by a server, or executed on the client-side using an `EndpointExecutor`.

Here is an Example:

```scala
package example

import zio._

import zio.http.Header.Authorization
import zio.http._
import zio.http.codec.HttpCodec
import zio.http.endpoint._

object EndpointExamples extends ZIOAppDefault {
  import HttpCodec._

  val auth = EndpointMiddleware.auth

  // MiddlewareSpec can be added at the service level as well
  val getUser =
    Endpoint.get("users" / int("userId")).out[Int] @@ auth

  val getUserRoute =
    getUser.implement { id =>
      ZIO.succeed(id)
    }

  val getUserPosts =
    Endpoint
      .get("users" / int("userId") / "posts" / int("postId"))
      .query(query("name"))
      .out[List[String]] @@ auth

  val getUserPostsRoute =
    getUserPosts.implement[Any] { case (id1: Int, id2: Int, query: String) =>
      ZIO.succeed(List(s"API2 RESULT parsed: users/$id1/posts/$id2?name=$query"))
    }

  val routes = getUserRoute ++ getUserPostsRoute

  val app = routes.toApp(auth.implement(_ => ZIO.unit)(_ => ZIO.unit))

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

      val result1: UIO[Int]          = executor(x1)
      val result2: UIO[List[String]] = executor(x2)

      result1.zip(result2).debug
    }
  }
}
```