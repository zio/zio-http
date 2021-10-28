import zhttp.http.Cookie.{httpOnly, maxAge, path, secure}
import zhttp.http._
import zhttp.service._
import zio._
import zio.duration.durationInt

/**
 * Example to make app using cookies
 */
object CookieServerSide extends App {

  // Setting cookies with an expiry of 5 days
  private val cookie = Cookie("key", "value") @@ maxAge(5 days)
  val res            = Response.ok.addCookie(cookie)

  private val app = HttpApp.collect {
    case Method.GET -> !! / "cookie" =>
      Response.ok.addCookie(cookie @@ path(!! / "cookie") @@ httpOnly)

    case Method.GET -> !! / "secure-cookie" =>
      Response.ok.addCookie(cookie @@ secure @@ path(!! / "secure-cookie"))

    case Method.GET -> !! / "cookie" / "remove" =>
      res.addCookie(cookie.clear)
  }

  // Run it like any simple app
  override def run(args: List[String]): URIO[zio.ZEnv, ExitCode] =
    Server.start(8090, app.silent).exitCode
}
