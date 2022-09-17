package zio.http.api

import zio._
import zio.http._

object Examples extends ZIOAppDefault {
  import In._

  val getUser =
    API.get(literal("users") / int).id("get-user")

  val getUsersService =
    getUser.handle { case (id: Int) =>
      ZIO.debug(s"API1 RESULT parsed: users/$id")
    }

  val getUserPosts =
    API
      .get(literal("users") / int / literal("posts") / query("name") / int)
      .id("get-user-posts")

  val getUserPostsService =
    getUserPosts.handle { case (id1, query, id2) =>
      ZIO.debug(s"API2 RESULT parsed: users/$id1/posts/$id2?name=$query")
    }

  val services = getUsersService ++ getUserPostsService

  val app = services.toHttpApp

  val request = Request(url = URL.fromString("/users/100/posts/200?name=adam").toOption.get)
  println(s"Looking up $request")

  val run = app(request).debug

  object Client {
    def example(client: Client) = {
      val registry =
        APIRegistry.empty.registerAll(APIAddress("localhost", 8080)) {
          getUser ++ getUserPosts
        }

      val executor = APIExecutor(client, registry)

      val x1 = getUser(42)
      val x2 = getUserPosts(42, "adam", 200)

      val result1 = executor(x1)
      val result2 = executor(x2)

      result1.zip(result2)
    }
  }
}
