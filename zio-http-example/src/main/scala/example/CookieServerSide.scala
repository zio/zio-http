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

  private val app = Routes(
    Method.GET / "cookie"            ->
      handler(Response.ok.addCookie(cookie.copy(path = Some(Root / "cookie"), isHttpOnly = true))),
    Method.GET / "secure-cookie"     ->
      handler(Response.ok.addCookie(cookie.copy(isSecure = true, path = Some(Root / "secure-cookie")))),
    Method.GET / "cookie" / "remove" ->
      handler(res.addCookie(Cookie.clear("key"))),
  ).toApp

  // Run it like any simple app
  val run =
    Server.serve(app).provide(Server.default)
}
