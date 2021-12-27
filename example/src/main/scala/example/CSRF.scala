package example

import zhttp.http.Middleware.csrf
import zhttp.http._
import zhttp.service.Server
import zio.{App, ExitCode, URIO}

object CSRF extends App {
  // Will give FORBIDDEN when CSRF token in header doesn't match CSRF token set in cookies
  val app: HttpApp[Any, Nothing] = Http.ok @@ csrf(headerName = "x-token", cookieName = "token")

  override def run(args: List[String]): URIO[zio.ZEnv, ExitCode] =
    Server.start(8090, app).exitCode
}
