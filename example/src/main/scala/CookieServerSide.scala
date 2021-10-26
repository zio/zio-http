import zhttp.http.CookieOps.{CookiePath, Domain, HttpOnly, MaxAge, Secure}
import zhttp.http._
import zhttp.service._
import zio._

import scala.concurrent.duration.DurationInt

/**
 * Example to make app using cookies
 */
object CookieServerSide extends App {
  val cookie =
    Cookie(name = "abc", content = "value", path = Some(Path("/cookie")), maxAge = Some(5 days))

  val cookie2 =
    Cookie("key", "value") @@ MaxAge(5 days) @@ Domain("/s") @@ Secure @@ HttpOnly @@ CookiePath(!! / "secure-cookie")

  val app = HttpApp.collect {
    case Method.GET -> !! / "cookie"            =>
      Response
        .text("Cookies added")
        .addCookie(cookie)
    case Method.GET -> !! / "secure-cookie"     =>
      Response
        .text("Cookies with secure true added")
        .addCookie(cookie2)
    case Method.GET -> !! / "cookie" / "remove" =>
      Response.text("Cookies removed").removeCookie("abc")
  }

  // Run it like any simple app
  override def run(args: List[String]): URIO[zio.ZEnv, ExitCode] =
    Server.start(8090, app.silent).exitCode
}
