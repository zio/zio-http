package zio.http.api

import zio._
import zio.http._
import zio.http.api.internal._

object Examples extends ZIOAppDefault {
  import In._

  val api1: HandledAPI[Any, Nothing, Int, Unit] =
    API.get(literal("users") / int).handle { case (id: Int) => ZIO.debug(s"API1 RESULT parsed: users/$id") }

  val api2: HandledAPI[Any, Nothing, (Int, String, Int), Unit] =
    API
      .get(literal("users") / int / literal("posts") / query("name") / int)
      .handle { case (id1, query, id2) =>
        ZIO.debug(s"API2 RESULT parsed: users/$id1/posts/$id2?name=$query")
      }

  def cast[R, E](handled: HandledAPI[R, E, _, _]): HandledAPI[R, E, Request, Response] =
    handled.asInstanceOf[HandledAPI[R, E, Request, Response]]

  val tree: HandlerTree[Any, Nothing] = HandlerTree.fromIterable(Chunk(cast(api1), cast(api2)))

  val request = Request(url = URL.fromString("/users/100/posts/200?name=adam").toOption.get)
  println(s"Looking up $request")

  val result = tree.lookup(request).get
  println(s"Match Results: ${result.results}")
  println(s"Match API: ${api2 == result.handledApi}")

  val run = result.run(request).debug("run")
}
