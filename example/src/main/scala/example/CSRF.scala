package example

import zhttp.http.Middleware.{csrfGenerate, csrfValidate}
import zhttp.http._
import zhttp.service.Server
import zio._

object CSRF extends ZIOAppDefault {
  val privateApp = Http.collect[Request] { case Method.GET -> !! / "unsafeEndpoint" =>
    Response.text("secure info")
  } @@ csrfValidate() // Check for matching csrf header and cookie

  val publicApp = Http.collect[Request] { case Method.GET -> !! / "safeEndpoint" =>
    Response.text("hello")
  } @@ csrfGenerate() // set x-csrf token cookie

  val app = publicApp ++ privateApp

  def run = Server.start(8090, app)
}
