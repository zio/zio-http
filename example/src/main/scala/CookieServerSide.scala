import zhttp.http._
import zhttp.service._
import zio._

import java.util.Date

/**
 * Example to make app using cookies
 */
object CookieServerSide extends App {
  val date                       = new Date(2625675999550L)
  val app: HttpApp[Any, Nothing] = HttpApp.collect {
    case Method.GET -> Root / "cookie"            =>
      Response.addCookie(
        Cookie(
          name = "abc",
          content = "value",
          expires = Some(date.toInstant),
          domain = None,
          path = Some(Path("/cookie")),
          httpOnly = true,
          maxAge = None,
          sameSite = None,
        ),
      )
    case Method.GET -> Root / "cookie" / "remove" =>
      Response.removeCookie("abc")
  }

  // Run it like any simple app
  override def run(args: List[String]): URIO[zio.ZEnv, ExitCode] =
    Server.start(8090, app.silent).exitCode
}
