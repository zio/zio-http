package zio.http.api

import zio._
import zio.http._

object APIExamples extends ZIOAppDefault {
  import RouteCodec._
  import QueryCodec._

  // MiddlewareSpec can be added at the service level as well
  val getUser =
    API.get(literal("users") / int).out[Int]

  val getUsersService =
    getUser.handle[Any, Nothing] { case (id: Int) =>
      ZIO.succeedNow(1)
    }

  val getUserPosts =
    API
      .get(literal("users") / int / literal("posts") / int)
      .in(query("name"))

  val getUserPostsService =
    getUserPosts.handle[Any, Nothing] { case (id1, query, id2) =>
      ZIO.debug(s"API2 RESULT parsed: users/$id1/posts/$id2?name=$query")
    }

  val serviceSpec = getUser ++ getUserPosts

  val app = serviceSpec.toHttpApp(getUsersService ++ getUserPostsService)

  val request = Request.get(url = URL.fromString("/users/1").toOption.get)
  println(s"Looking up $request")

  val run = Server.serve(app).provide(Server.default)

  object Client {
    def example(client: Client) = {
      val registry =
        APIRegistry.empty.registerAll(URL.fromString("http://localhost:8080").getOrElse(???)) {
          serviceSpec
        }

      val executor: APIExecutor[getUser.Id with getUserPosts.Id] = APIExecutor(client, registry)

      val x1 = getUser(42)
      val x2 = getUserPosts(42, 200, "adam")

      val result1 = executor(x1)
      val result2 = executor(x2)

      result1.zip(result2)
    }
  }
}
