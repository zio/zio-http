package example

import zio._
import zio.http._
import zio.http.model.Method

object CSRF extends ZIOAppDefault {
  val privateApp = Http
    .collect[Request] { case Method.GET -> !! / "unsafeEndpoint" =>
      Response.text("secure info")
    }
    .withMiddleware(api.Middleware.csrfValidate())

  val publicApp = Http
    .collect[Request] { case Method.GET -> !! / "safeEndpoint" =>
      Response.text("hello")
    }
    .withMiddleware(api.Middleware.csrfGenerate()) // set x-csrf token cookie

  val app = (publicApp ++ privateApp).withDefaultErrorResponse

  def run = Server.serve(app).provide(Server.default)
}
