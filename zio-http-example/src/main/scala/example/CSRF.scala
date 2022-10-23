package example

import zio._
import zio.http.Middleware.{csrfGenerate, csrfValidate}
import zio.http._
import zio.http.model.Method

object CSRF extends ZIOAppDefault {
  val privateApp = csrfValidate().apply(Http.collect[Request] { case Method.GET -> !! / "unsafeEndpoint" =>
    Response.text("secure info")
  }) // Check for matching csrf header and cookie

  val publicApp = Http.collect[Request] { case Method.GET -> !! / "safeEndpoint" =>
    Response.text("hello")
  } @@ csrfGenerate() // set x-csrf token cookie

  val app = publicApp ++ privateApp

  def run = Server.serve(app).provide(Server.default)
}
