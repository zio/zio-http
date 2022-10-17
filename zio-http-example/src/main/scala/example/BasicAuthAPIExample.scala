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
    getUser.implement { case (id: Int) =>
      ZIO.succeed(id)
    }

  val authMiddleware = MiddlewareSpec.auth
  val correlationId  = MiddlewareSpec.addCorrelationId

  val middleware: MiddlewareSpec[Auth.Credentials, String] =
    MiddlewareSpec.auth ++ MiddlewareSpec.addCorrelationId

  val authMiddlewareHandler =
    authMiddleware.implement(_ => ZIO.unit)

  val correlationIdHandler =
    correlationId.implement(_ => ZIO.succeed("qdbasdjkansdad"))

  val serviceSpec = getUser.toServiceSpec.middleware(middleware)

  val app = serviceSpec.toHttpApp(getUserHandler, authMiddlewareHandler ++ correlationIdHandler)

  val run = Server.serve(app).provide(Server.default)

}
