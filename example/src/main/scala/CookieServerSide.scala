import zhttp.http.Cookie.{httpOnly, maxAge, path, secure}
import zhttp.http._
import zhttp.service._
import zio._

import scala.concurrent.duration.DurationInt

/**
 * Example to make app using cookies
 */
object CookieServerSide extends App {
  val cookie = Cookie("key", "value") @@ maxAge(5 days) @@ httpOnly @@ path(!! / "cookie")
  val app    = HttpApp.collect {
    case Method.GET -> !! / "cookie"            =>
      Response
        .text("Cookies added")
        .addCookie(cookie)
    case Method.GET -> !! / "secure-cookie"     =>
      Response
        .text("Cookies with secure true added")
        .addCookie(cookie @@ secure)
    case Method.GET -> !! / "cookie" / "remove" =>
      Response.text("Cookies removed").removeCookie("key")
  }

  // Run it like any simple app
  override def run(args: List[String]): URIO[zio.ZEnv, ExitCode] =
    Server.start(8090, app.silent).exitCode
}
