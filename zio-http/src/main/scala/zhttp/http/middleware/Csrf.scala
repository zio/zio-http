package zhttp.http.middleware

import zhttp.http._
import zio.ZIO

import java.util.UUID

private[zhttp] trait Csrf {

  /**
   * Generates a new CSRF token that can be validated using the csrfValidate
   * middleware.
   *
   * CSRF middlewares: To prevent Cross-site request forgery attacks. This
   * middleware is modeled after the double submit cookie pattern. Used in
   * conjunction with [[#csrfValidate]] middleware.
   *
   * https://cheatsheetseries.owasp.org/cheatsheets/Cross-Site_Request_Forgery_Prevention_Cheat_Sheet.html#double-submit-cookie
   */
  final def csrfGenerate[R, E](
    tokenName: String = "x-csrf-token",
    tokenGen: ZIO[R, Nothing, String] = ZIO.succeed(UUID.randomUUID.toString),
  ): HttpMiddleware[R, E] =
    Middleware.addCookieZIO(tokenGen.map(Cookie(tokenName, _)))

  /**
   * Validates the CSRF token appearing in the request headers. Typically the
   * token should be set using the `csrfGenerate` middleware.
   *
   * CSRF middlewares : To prevent Cross-site request forgery attacks. This
   * middleware is modeled after the double submit cookie pattern. Used in
   * conjunction with [[#csrfGenerate]] middleware
   *
   * https://cheatsheetseries.owasp.org/cheatsheets/Cross-Site_Request_Forgery_Prevention_Cheat_Sheet.html#double-submit-cookie
   */
  def csrfValidate(tokenName: String = "x-csrf-token"): HttpMiddleware[Any, Nothing] = {
    Middleware.whenHeader(
      headers => {
        (headers.headerValue(tokenName), headers.cookieValue(tokenName)) match {
          case (Some(headerValue), Some(cookieValue)) => headerValue != cookieValue
          case _                                      => true
        }
      },
      Middleware.succeed(Response.status(Status.Forbidden)),
    )
  }
}
