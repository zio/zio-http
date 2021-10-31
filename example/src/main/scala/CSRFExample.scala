import zhttp.http._
import zhttp.http.middleware.CSRF.CSRF
import zhttp.http.middleware.CSRF.CSRF.CookieSetting
import zhttp.http.middleware.HttpMiddleware
import zhttp.service.Server
import zio._

object CSRFExample extends App {
  val app: HttpApp[Any, Nothing] = HttpApp.collectM {
    case Method.GET -> !! / "safeEndpoint"    =>
      UIO(Response.ok)
    case Method.POST -> !! / "unsafeEndpoint" => UIO(Response.ok)
  }
  val csrf: CSRF                 = CSRF("x-csrf", CookieSetting("csrf-token"), () => UIO("token"))
  val generateTokenMiddleware: HttpMiddleware[Any, Nothing] = csrf.generateToken
  val ValidateTokenMiddleware: HttpMiddleware[Any, Nothing] = csrf.checkToken

  val appNew =
    app @@ generateTokenMiddleware.when((method, _, _) => method == Method.GET) @@ ValidateTokenMiddleware.when(
      (method, _, _) => method == Method.POST,
    )

  override def run(args: List[String]): URIO[zio.ZEnv, ExitCode] =
    Server.start(8090, appNew).exitCode
}
