package zio.http.api

import zio._
import zio.http.middleware.Auth
import zio.http.{Client, Request, Server, URL}

object APIExamples extends ZIOAppDefault {
  import RouteCodec._
  import QueryCodec._

  // MiddlewareSpec can be added at the service level as well
  val getUsers =
    EndpointSpec.get("users" / int("userId")).out[Int]

  val getUserEndpoint =
    getUsers.implement { id =>
      ZIO.succeed(id)
    }

  val getUserPosts =
    EndpointSpec
      .get("users" / int("userId") / "posts" / int("postId"))
      .in(query("name"))

  val getUserPostEndpoint =
    getUserPosts.implement[Any, Nothing] { case (id1: Int, id2: Int, query: String) =>
      ZIO.debug(s"API2 RESULT parsed: users/$id1/posts/$id2?name=$query")
    }

  val middlewareSpec =
    MiddlewareSpec.auth

  // just like api.handle
  val middleware =
    middlewareSpec.implementIncoming(_ => ZIO.unit)

  val serviceSpec =
    (getUsers.toServiceSpec ++ getUserPosts.toServiceSpec).middleware(middlewareSpec)

  val app = serviceSpec.toHttpApp(getUserEndpoint ++ getUserPostEndpoint, middleware)

  val request = Request.get(url = URL.fromString("/users/1").toOption.get)
  println(s"Looking up $request")

  val run = Server.serve(app).provide(Server.default)

  object Client {
    def example(client: Client) = {
      val registry =
        EndpointRegistry(URL.fromString("http://localhost:8080").getOrElse(???), serviceSpec)

      val executor: EndpointExecutor[Any, Any, getUsers.type with getUserPosts.type] =
        EndpointExecutor(client, registry, ZIO.succeed(Auth.Credentials("user", "pass")))

      val x1 = getUsers(42)
      val x2 = getUserPosts(42, 200, "adam")

      val result1 = executor(x1)
      val result2 = executor(x2)

      result1.zip(result2)
    }
  }
}
