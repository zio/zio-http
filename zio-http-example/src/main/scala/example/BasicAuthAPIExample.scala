package zio.http.api

import zio._
import zio.http._
import zio.http.middleware.Auth

object BasicAuthAPIExample extends ZIOAppDefault {

  import RouteCodec._

  // MiddlewareSpec can be added at the service level as well
  val getUser =
    EndpointSpec.get(literal("users") / int).out[Int]

  val getUserImpl =
    getUser.implement { case (id: Int) =>
      ZIO.succeed(id)
    }

  val authMiddleware = MiddlewareSpec.auth
  val correlationId  = MiddlewareSpec.addCorrelationId

  val middleware: MiddlewareSpec[Auth.Credentials, String] =
    MiddlewareSpec.auth ++ MiddlewareSpec.addCorrelationId

  val authMiddlewareHandler: api.Middleware[Any, Nothing, Auth.Credentials, Unit] =
    authMiddleware.implement(_ => ZIO.unit)

  val correlationIdHandler: api.Middleware[Any, Nothing, Unit, String] =
    correlationId.implement(_ => ZIO.succeed("xyz"))

  val middlewareImpl: api.Middleware[Any, Nothing, Auth.Credentials, String] = {
    // FIXME: Discuss Can also be implemented through  `middleware.implement(cred => ZIO.succeed(cred.uname))` in
    // which correlation id becomes username. Do we support this?
    authMiddlewareHandler ++ correlationIdHandler
  }

  val serviceSpec = getUser.toServiceSpec.middleware(middleware)

  val app = serviceSpec.toHttpApp(getUserImpl, middlewareImpl)

  val run = Server.serve(app).provide(Server.default)

}
