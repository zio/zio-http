package zio.http.api

import zio._
import zio.http._

object APIExamples extends ZIOAppDefault {
  import In._

  val getUser =
    API.get(literal("users") / int)

  val getUsersService =
    getUser.handle[Any, Nothing] { case (id: Int) =>
      ZIO.debug(s"API1 RESULT parsed: users/$id")
    }

  val getUserPosts =
    API
      .get(literal("users") / int / literal("posts") / query("name") / int)

  val getUserPostsService =
    getUserPosts.handle[Any, Nothing] { case (id1, query, id2) =>
      ZIO.debug(s"API2 RESULT parsed: users/$id1/posts/$id2?name=$query")
    }

  val services = getUsersService ++ getUserPostsService

  val app = services.toHttpApp

  val request = Request.get(url = URL.fromString("/users/100/posts/200?name=adam").toOption.get)
  println(s"Looking up $request")

  val run = app(request).debug

  object Client {
    def example(client: Client) = {
      val registry =
        APIRegistry.empty.registerAll(URL.fromString("http://localhost:8080").getOrElse(???)) {
          getUser ++ getUserPosts
        }

      val executor: APIExecutor[getUser.Id with getUserPosts.Id] = APIExecutor(client, registry)

      val x1 = getUser(42)
      val x2 = getUserPosts(42, "adam", 200)

      val result1 = executor(x1)
      val result2 = executor(x2)

      result1.zip(result2)
    }
  }
}
