package example

import zhttp.http.{Cookie, _}
import zhttp.service.Server
import zio._

/**
 * Example to make app using cookies
 */
object CookieServerSide extends ZIOAppDefault {

  // Setting cookies with an expiry of 5 days
  private val cookie = Cookie("key", "value").withMaxAge(5 days)
  val res            = Response.ok.addCookie(cookie)

  private val app = Http.collect[Request] {
    case Method.GET -> !! / "cookie" =>
      Response.ok.addCookie(cookie.withPath(!! / "cookie").withHttpOnly(true))

    case Method.GET -> !! / "secure-cookie" =>
      Response.ok.addCookie(cookie.withSecure(true).withPath(!! / "secure-cookie"))

    case Method.GET -> !! / "cookie" / "remove" =>
      res.addCookie(Cookie.clear("key"))
  }

  // Run it like any simple app
  val run =
    Server.start(8090, app)
}
