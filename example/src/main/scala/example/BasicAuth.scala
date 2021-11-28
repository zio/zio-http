package example

import zhttp.http.HttpMiddleware.basicAuth
import zhttp.http._
import zhttp.service.Server
import zio.{App, ExitCode, URIO}

object BasicAuth extends App {

  // Http app that requires a JWT claim
  val user: UHttpApp = HttpApp.collect { case Method.GET -> !! / "user" / name / "greet" =>
    Response.text(s"Welcome to the ZIO party! ${name}")
  }

  // Composing all the HttpApps together
  val app: UHttpApp = user @@ basicAuth("admin", "admin")

  // Run it like any simple app
  override def run(args: List[String]): URIO[zio.ZEnv, ExitCode] =
    Server.start(8090, app).exitCode
}
