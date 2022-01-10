package zhttp.http.middleware

import zhttp.http._
import zio.{UIO, ZIO}

import java.util.UUID

trait CsrfMiddlewares {

  /**
   * CSRF middlewares : To prevent Cross-site request forgery attacks. This middleware is modeled after the double
   * submit cookie pattern.
   *
   * @see
   *   [[#csrfGenerate]] - Sets cookie with CSRF token
   * @see
   *   [[#csrfValidate]] - Validate token value in request headers against value in cookies
   * @see
   *   https://cheatsheetseries.owasp.org/cheatsheets/Cross-Site_Request_Forgery_Prevention_Cheat_Sheet.html#double-submit-cookie
   */

  def csrfGenerate[R, E](
    tokenName: String = "x-csrf-token",
    tokenGen: ZIO[R, Nothing, String] = UIO(UUID.randomUUID.toString),
  ): HttpMiddleware[R, E] =
    Middleware.addCookieM(tokenGen.map(Cookie(tokenName, _)))

  def csrfValidate(tokenName: String = "x-csrf-token"): HttpMiddleware[Any, Nothing] = {
    Middleware.whenHeader(
      headers => {
        (headers.getHeaderValue(tokenName), headers.getCookieValue(tokenName)) match {
          case (Some(headerValue), Some(cookieValue)) => headerValue != cookieValue
          case _                                      => true
        }
      },
      Middleware.succeed(Response.status(Status.FORBIDDEN)),
    )
  }
}
