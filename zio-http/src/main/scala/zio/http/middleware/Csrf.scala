package zio.http.middleware

import zio.http._
import zio.http.api.Middleware.Control
import zio.http.api.{HeaderCodec, HttpCodec, MiddlewareSpec, TextCodec}
import zio.http.model._
import zio.{Trace, ZIO}

import java.util.UUID
import zio.stacktracer.TracingImplicits.disableAutoTrace // scalafix:ok;

private[zio] trait Csrf {

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
    tokenGen: ZIO[R, Nothing, String] = ZIO.succeed(UUID.randomUUID.toString)(Trace.empty),
  )(implicit trace: Trace): HttpMiddleware[R, E] =
    Middleware.addCookieZIO(tokenGen.map(Cookie(tokenName, _)))

  // TODO; Remove csrfGenerate and rename csrfGenerate_ to csrfGenerate
  final def csrfGenerate_[R, E](
    tokenName: String = "x-csrf-token",
    tokenGen: ZIO[R, Nothing, String] = ZIO.succeed(UUID.randomUUID.toString)(Trace.empty),
  )(implicit trace: Trace): api.Middleware[R, Nothing, Unit, Cookie[Response]] = {
    api.Middleware.addCookieZIO(tokenGen.map(Cookie(tokenName, _)))
  }

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
  def csrfValidate(
    tokenName: String = "x-csrf-token",
  )(implicit trace: Trace): api.Middleware[Any, Nothing, Option[(String, String)], Unit] = {
    val cookie: HeaderCodec[String] =
      HeaderCodec.cookie

    val tokenHeader =
      HeaderCodec.header(tokenName, TextCodec.string)

    val middleware: MiddlewareSpec[Option[(String, String)], Unit] =
      MiddlewareSpec(
        (cookie ++ tokenHeader).optional,
        HttpCodec.empty,
      )

    middleware.implement[Option[(String, String)]] {
      case state @ (Some((headerValue, cookieValue))) if headerValue != cookieValue =>
        Control.Continue[Option[(String, String)]](state)

      case state =>
        Control.Abort(state, _ => Response.status(Status.Forbidden))

    }((_, _) => ())
  }
}
