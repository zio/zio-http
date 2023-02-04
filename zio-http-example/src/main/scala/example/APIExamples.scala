package zio.http.api

import zio._
import zio.http.middleware.Auth
import zio.http.model.headers.values.WWWAuthenticate
import zio.http.{Client, Request, Server, URL}

object APIExamples extends ZIOAppDefault {
  import RouteCodec._
  import QueryCodec._

  val middleware = EndpointMiddleware.auth

  // MiddlewareSpec can be added at the service level as well
  val getUser =
    Endpoint.get("users" / int("userId")).out[Int] @@ middleware

  val getUserRoute =
    getUser.implement { id =>
      ZIO.succeed(id)
    }

  val getUserPosts =
    Endpoint
      .get("users" / int("userId") / "posts" / int("postId"))
      .in(query("name"))
      .out[List[String]] @@ middleware

  val getUserPostsRoute =
    getUserPosts.implement[Any] { case (id1: Int, id2: Int, query: String) =>
      ZIO.succeed(List(s"API2 RESULT parsed: users/$id1/posts/$id2?name=$query"))
    }

  val routes = getUserRoute ++ getUserPostsRoute

  val app = routes.toHttpApp

  val request = Request.get(url = URL.fromString("/users/1").toOption.get)

  val run = Server.serve(app).provide(Server.default)

  object Client {
    def example(client: Client) = {
      val locator =
        EndpointLocator.fromURL(URL.fromString("http://localhost:8080").toOption.get)

      val executor: EndpointExecutor[WWWAuthenticate] =
        EndpointExecutor(client, locator, ZIO.succeed(WWWAuthenticate.Basic("user", "pass")))

      val x1 = getUser(42)
      val x2 = getUserPosts(42, 200, "adam")

      val result1: UIO[Int]          = executor(x1)
      val result2: UIO[List[String]] = executor(x2)

      result1.zip(result2).debug
    }
  }
}
