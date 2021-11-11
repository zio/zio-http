import zhttp.http._
import zhttp.http.middleware.HttpMiddleware.basicAuth
import zhttp.service.Server
import zio._

object Authentication extends App {

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
