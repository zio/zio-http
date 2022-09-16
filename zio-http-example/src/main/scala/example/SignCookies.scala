package example

import zio._
import zio.http._
import zio.http.model.{Cookie, Method, Request, Response}

/**
 * Example to make app using signed-cookies
 */
object SignCookies extends ZIOAppDefault {

  // Setting cookies with an expiry of 5 days
  private val cookie = Cookie("key", "hello").withMaxAge(5 days)

  private val app = Http.collect[Request] { case Method.GET -> !! / "cookie" =>
    Response.ok.addCookie(cookie.sign("secret"))
  }

  // Run it like any simple app
  val run = Server.start(8090, app)
}
