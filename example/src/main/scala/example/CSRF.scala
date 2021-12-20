package example

import zhttp.http.Middleware.csrf
import zhttp.http._
import zhttp.service.Server
import zio.{App, ExitCode, URIO}

object CSRF extends App {
  // CSRF middleware
  // To prevent Cross-site request forgery attacks. This middleware is modeled after the double submit cookie pattern.
  // https://cheatsheetseries.owasp.org/cheatsheets/Cross-Site_Request_Forgery_Prevention_Cheat_Sheet.html#double-submit-cookie
  val app: HttpApp[Any, Nothing] = Http.ok @@ csrf("x-token", "token")

  override def run(args: List[String]): URIO[zio.ZEnv, ExitCode] =
    Server.start(8090, app).exitCode
}
