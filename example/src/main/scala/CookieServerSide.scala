import zhttp.http._
import zhttp.service._
import zio._

/**
 * Example to make app using cookies
 */
object CookieServerSide extends App {
  val app: HttpApp[Any, Nothing] = HttpApp.collect {
    case Method.GET -> !! / "cookie"            =>
      Response.addSetCookie(
        Cookie(
          name = "abc",
          content = "value",
          expires = None,
          domain = None,
          path = Some(Path("/cookie")),
          httpOnly = true,
          maxAge = None,
          sameSite = None,
        ),
      )
    case Method.GET -> !! / "cookie" / "remove" =>
      Response.removeSetCookie("abc")
  }

  // Run it like any simple app
  override def run(args: List[String]): URIO[zio.ZEnv, ExitCode] =
    Server.start(8090, app.silent).exitCode
}
