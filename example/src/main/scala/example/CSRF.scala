package example

import zhttp.http.Middleware.{addCookie, csrf}
import zhttp.http._
import zhttp.service.Server
import zio.{App, ExitCode, UIO, URIO}

object CSRF extends App {
  val app: HttpApp[Any, Nothing] = Http.collectM {
    case Method.GET -> !! / "safeEndpoint"    =>
      UIO(Response.ok)
    case Method.POST -> !! / "unsafeEndpoint" => UIO(Response.ok)
  }
  app @@ csrf("x-token", "token").when((method, _, _) => method == Method.POST) @@
    addCookie(Cookie("token", "randomToken")).when((method, _, _) => method == Method.GET)

  override def run(args: List[String]): URIO[zio.ZEnv, ExitCode] =
    Server.start(8091, app).exitCode
}
