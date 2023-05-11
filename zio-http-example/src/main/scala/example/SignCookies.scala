package example

import zio._

import zio.http._

/**
 * Example to make app using signed-cookies
 */
object SignCookies extends ZIOAppDefault {

  // Setting cookies with an expiry of 5 days
  private val cookie = Cookie.Response("key", "hello", maxAge = Some(5 days))

  private val app = Http.collect[Request] { case Method.GET -> Root / "cookie" =>
    Response.ok.addCookie(cookie.sign("secret"))
  }

  // Run it like any simple app
  val run = Server.serve(app).provide(Server.default)
}
