package zio.http.api

import zio._
import zio.http._
import zio.http.middleware.Auth

object BasicAuthAPIExample extends ZIOAppDefault {

  import RouteCodec._

  // MiddlewareSpec can be added at the service level as well
  val getUser =
    API.get(literal("users") / int).out[Int]

  val getUserHandler =
    getUser.handle[Any, Nothing] { case (id: Int) =>
      ZIO.succeed(id)
    }

  val authMiddleware: MiddlewareSpec[Auth.Credentials, Unit] =
    MiddlewareSpec.basicAuth("admin", "admin")

  val authMiddlewareHandler =
    authMiddleware.handle(_ => ZIO.succeed(()))

  val serviceSpec = getUser.middleware(authMiddleware)

  val app = serviceSpec.toHttpApp(getUserHandler, authMiddlewareHandler)

  val run = Server.serve(app).provide(Server.default)

}
