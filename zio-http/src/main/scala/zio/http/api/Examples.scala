package zio.http.api

import zio._
import zio.http._

object Examples extends ZIOAppDefault {
  import In._

  val api1 =
    API.get(literal("users") / int).handle { case (id: Int) => ZIO.debug(s"API1 RESULT parsed: users/$id") }

  val api2 =
    API
      .get(literal("users") / int / literal("posts") / query("name") / int)
      .handle { case (id1, query, id2) =>
        ZIO.debug(s"API2 RESULT parsed: users/$id1/posts/$id2?name=$query")
      }

  val apis = api1 ++ api2

  val app = apis.toHttpApp

  val request = Request(url = URL.fromString("/users/100/posts/200?name=adam").toOption.get)
  println(s"Looking up $request")

  val run = app(request).debug
}
