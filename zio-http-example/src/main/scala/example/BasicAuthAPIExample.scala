package zio.http.api

import zio._
import zio.http._

object BasicAuthAPIExample extends ZIOAppDefault {

  import In._

  // MiddlewareSpec can be added at the service level as well
  val getUser =
    API.get(literal("users") / int).out[Int] @@ MiddlewareSpec.addBasicAuth("user", "test")

  val getUsersService =
    getUser.handle[Any, Nothing] { case (id: Int) =>
      ZIO.succeedNow(1)
    }

  val app = getUsersService.toHttpApp

  val run = Server.serve(app).provide(Server.default)

}
