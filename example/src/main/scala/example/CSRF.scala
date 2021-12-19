package example

import zhttp.http.Middleware.{addCookie, csrf}
import zhttp.http._
import zhttp.service.Server
import zio.{App, ExitCode, URIO}

object CSRF extends App {
  val app: HttpApp[Any, Nothing] =
    Http.collect[Request] {
      case Method.GET -> !! / "safeEndpoint"    => Response.text("Hello World!")
      case Method.POST -> !! / "unSafeEndpoint" => Response.jsonString("""{"greetings": "Hello World!"}""")
    } @@ addCookie(Cookie("token", "randomToken")).when((method, _, _) => method == Method.GET) @@ csrf(
      "x-token",
      "token",
    ).when((method, _, _) => method == Method.POST)

  override def run(args: List[String]): URIO[zio.ZEnv, ExitCode] =
    Server.start(8091, app).exitCode
}
