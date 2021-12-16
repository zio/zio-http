package example

import zhttp.http.Cookie.{httpOnly, maxAge, path, secure}
import zhttp.http.Middleware.signCookies
import zhttp.http.{Cookie, Method, Response, _}
import zhttp.service.Server
import zio.duration.durationInt
import zio.{App, ExitCode, URIO}

/**
 * Example to make app using cookies
 */
object SignCookies extends App {

  // Setting cookies with an expiry of 5 days
  private val cookie  = Cookie("key", "value") @@ maxAge(5 days)
  private val cookie2 = Cookie("key2", "value2") @@ maxAge(5 days)

  private val app = Http.collect[Request] {
    case Method.GET -> !! / "cookie" =>
      Response.ok.addCookie(cookie @@ path(!! / "cookie") @@ httpOnly).addCookie(cookie2)

    case req @ Method.GET -> !! / "secure-cookie" => {
      val res = Response.ok.addCookie(cookie @@ secure @@ path(!! / "secure-cookie")).addCookie(cookie2)
      println(req.getSignedCookies("secret"))
      res
    }
  }
  // signing cookies with a secret
  val newApp      = app @@ signCookies("secret")

  // Run it like any simple app
  override def run(args: List[String]): URIO[zio.ZEnv, ExitCode] =
    Server.start(8090, newApp.silent).exitCode
}
