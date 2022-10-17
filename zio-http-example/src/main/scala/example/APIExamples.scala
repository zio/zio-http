package zio.http.api

import zio._
import zio.http.middleware.Auth
import zio.http.{Client, Request, Server, URL}

object APIExamples extends ZIOAppDefault {
  import RouteCodec._
  import QueryCodec._

  // MiddlewareSpec can be added at the service level as well
  val getUser =
    API.get(literal("users") / int).out[Int]

  val getUsersService =
    getUser.implement[Any, Nothing] { case (id: Int) =>
      ZIO.succeedNow(id)
    }

  val getUserPosts =
    API
      .get(literal("users") / int / literal("posts") / int)
      .in(query("name"))

  val getUserPostsService =
    getUserPosts.implement[Any, Nothing] { case (id1, query, id2) =>
      ZIO.debug(s"API2 RESULT parsed: users/$id1/posts/$id2?name=$query")
    }

  val middleware =
    MiddlewareSpec.auth

  // just like api.handle
  val middlewareImpl =
    middleware.implement(_ => ZIO.unit)

  val serviceSpec = (getUser ++ getUserPosts).middleware(middleware)

  val app = serviceSpec.toHttpApp(getUsersService ++ getUserPostsService, middlewareImpl)

  val request = Request.get(url = URL.fromString("/users/1").toOption.get)
  println(s"Looking up $request")

  val run = Server.serve(app).provide(Server.default)

  object Client {
    def example(client: Client) = {
      val registry =
        APIRegistry(URL.fromString("http://localhost:8080").getOrElse(???), serviceSpec)

      val executor: APIExecutor[Any, Any, getUser.Id with getUserPosts.Id] =
        APIExecutor(client, registry, ZIO.succeed(Auth.Credentials("user", "pass")))

      val x1 = getUser(42)
      val x2 = getUserPosts(42, 200, "adam")

      val result1 = executor(x1)
      val result2 = executor(x2)

      result1.zip(result2)
    }
  }
}
