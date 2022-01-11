package example

import zhttp.http.{Cookie, Method, Response, _}
import zhttp.service.Server
import zio.duration.durationInt
import zio.{App, ExitCode, URIO}

/**
 * Example to make app using signed-cookies
 */
object SignCookies extends App {

  // Setting cookies with an expiry of 5 days
  private val cookie = Cookie("key", "hello").withMaxAge(5 days)

  private val app = Http.collect[Request] { case Method.GET -> !! / "cookie" =>
    Response.ok.addCookie(cookie.sign("secret"))
  }

  // Run it like any simple app
  override def run(args: List[String]): URIO[zio.ZEnv, ExitCode] =
    Server.start(8090, app).exitCode
}
