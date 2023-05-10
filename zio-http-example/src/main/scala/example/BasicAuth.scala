package example

import zio._

import zio.http.HttpAppMiddleware.basicAuth
import zio.http._

object BasicAuth extends ZIOAppDefault {

  // Http app that requires a JWT claim
  val user: HttpApp[Any, Nothing] = Http.collect[Request] { case Method.GET -> Root / "user" / name / "greet" =>
    Response.text(s"Welcome to the ZIO party! ${name}")
  }

  // Composing all the HttpApps together
  val app: HttpApp[Any, Nothing] = user @@ basicAuth("admin", "admin")

  // Run it like any simple app
  val run = Server.serve(app).provide(Server.default)
}
