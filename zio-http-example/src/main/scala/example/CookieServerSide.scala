package example

import zio._

import zio.http._

/**
 * Example to make app using cookies
 */
object CookieServerSide extends ZIOAppDefault {

  // Setting cookies with an expiry of 5 days
  private val cookie = Cookie.Response("key", "value", maxAge = Some(5 days))
  val res            = Response.ok.addCookie(cookie)

  private val app = Http.collect[Request] {
    case Method.GET -> !! / "cookie" =>
      Response.ok.addCookie(cookie.copy(path = Some(!! / "cookie"), isHttpOnly = true))

    case Method.GET -> !! / "secure-cookie" =>
      Response.ok.addCookie(cookie.copy(isSecure = true, path = Some(!! / "secure-cookie")))

    case Method.GET -> !! / "cookie" / "remove" =>
      res.addCookie(Cookie.clear("key"))
  }

  // Run it like any simple app
  val run =
    Server.serve(app).provide(Server.default)
}
