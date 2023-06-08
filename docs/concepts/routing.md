---
id: routing
title: Routing
---

# Routing 

ZIO-HTTP provides a powerful and expressive routing system for defining HTTP routes and handling requests. It leverages the capabilities of the ZIO functional programming library to provide a purely functional and composable approach to routing.

In ZIO-HTTP, routing is based on the concept of "endpoints." An endpoint represents a specific combination of an HTTP method, URL path pattern, and input/output types. It defines how to handle incoming requests that match the specified method and path.

The core abstraction for defining endpoints in ZIO-HTTP is the Endpoint type, which is built using a DSL (Domain Specific Language) provided by the library. The DSL allows you to define endpoints and combine them together to create more complex routing configurations.

Here's an example of how you can define an endpoint using ZIO-HTTP:

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

In the given example above, the concepts of routing in ZIO-HTTP are demonstrated using the ZIO and zio-http libraries. Let's go through the code to understand these concepts:

- `Endpoint`: An Endpoint represents a specific HTTP endpoint that your application can handle. It consists of a combination of path segments, query parameters, request and response codecs, and middleware. Endpoints define the structure of the HTTP request and response.

- `EndpointMiddleware`: EndpointMiddleware is a helper object that provides middleware functions for endpoints. Middleware allows you to add additional functionality to your endpoints, such as authentication, logging, error handling, etc.

- `getUser`: This is an example of an endpoint defined using the Endpoint object. It represents a GET request to the `"/users/{userId}"` path, where `"{userId}"` is a path parameter of type Int. The `@@` operator is used to add middleware (auth in this case) to the endpoint.

- `getUserRoute`: This defines the implementation of the getUser endpoint. The implementation is a ZIO effect that takes an Int as input and returns a successful ZIO effect with the same Int value. This implementation can be any business logic you want to execute when the endpoint is called.

- `getUserPosts`: This is another example of an endpoint representing a GET request to the `"/users/{userId}/posts/{postId}"` path. It also includes a query parameter named `"name"`. The response type is `List[String]`. The `@@` operator is used to add the auth middleware to this endpoint as well.

- `getUserPostsRoute`: This defines the implementation of the getUserPosts endpoint. The implementation takes three input parameters: `id1: Int, id2: Int, and query: String`. It returns a successful ZIO effect that constructs a list of strings based on the input parameters.

- `routes`: This combines the getUserRoute and getUserPostsRoute endpoints into a single Routes object. The `++` operator is used to concatenate the two routes.

- `app`: This converts the routes object into a ZIO HttpApp by using the toApp method. The toApp method takes an additional auth.implement function, which provides the implementation for the authentication middleware. In this case, it's a simple implementation that returns ZIO.unit.

- `request`: This creates an example HTTP request to the `"/users/1"` path using the Request.get method.

- `run`: This sets up a server using the Server.serve method, passing in the app and a default server configuration.

- `ClientExample`: This is an example client code that demonstrates how to interact with the server using the defined endpoints. It uses an EndpointLocator to map endpoint paths to URLs and an EndpointExecutor to execute the endpoints on the server.

- `example`: This method takes a Client as input and demonstrates how to execute the getUser and getUserPosts endpoints using the EndpointExecutor. It sets up the executor with a basic authentication header and executes the endpoints, resulting in two ZIO effects: `result1 (type UIO[Int])` and `result2 (type UIO[List[String]])`.

The example code shows how to define and implement endpoints using ZIO-HTTP, how to set up a server to handle those endpoints, and how to execute those endpoints using a client. It also demonstrates the use of middleware for authentication (auth), path parameters, query parameters, and response types.
