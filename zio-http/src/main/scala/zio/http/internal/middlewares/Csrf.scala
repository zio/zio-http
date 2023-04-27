package zio.http.internal.middlewares

import java.util.UUID

import zio._
import zio.http.{
  Cookie,
  Handler,
  Header,
  HttpError,
  Request,
  RequestHandlerMiddleware,
  RequestHandlerMiddlewares,
  Response,
  Status,
}

private[zio] trait Csrf {
  @inline
  final val csrfName = "x-csrf-token"

  /**
   * Generate a CSRF token which can be validated by the [[#csrfValidate]]
   * middleware.
   *
   * CSRF middlewares: To prevent Cross-site request forgery attacks. This
   * middleware is modeled after the double submit cookie pattern. Used in
   * conjunction with [[#csrfValidate]] middleware.
   *
   * https://cheatsheetseries.owasp.org/cheatsheets/Cross-Site_Request_Forgery_Prevention_Cheat_Sheet.html#double-submit-cookie
   */
  final def csrfGenerate[R, E](
    name: String = csrfName,
    tokenGen: ZIO[R, E, String] = ZIO.succeed(UUID.randomUUID.toString)(Trace.empty),
  ): RequestHandlerMiddleware[Nothing, R, E, Any] =
    new RequestHandlerMiddleware.Simple[R, E] {
      override def apply[R1 <: R, E1 >: E](handler: Handler[R1, E1, Request, Response])(implicit
        trace: zio.Trace,
      ): Handler[R1, E1, Request, Response] = {
        Handler.fromFunctionZIO[Request] { request =>
          tokenGen.map(Cookie.Response(name, _)).flatMap { cookie =>
            handler.runZIO(request).map(_.addCookie(cookie))
          }
        }
      }
    }

  /**
   * Validate a CSRF token which was created by the [[#csrfGenerate]]
   * middleware.
   *
   * CSRF middlewares: To prevent Cross-site request forgery attacks. This
   * middleware is modeled after the double submit cookie pattern. Used in
   * conjunction with [[#csrfValidate]] middleware.
   *
   * https://cheatsheetseries.owasp.org/cheatsheets/Cross-Site_Request_Forgery_Prevention_Cheat_Sheet.html#double-submit-cookie
   */
  final def csrfValidate(
    name: String = csrfName,
    status: Status = Status.Forbidden,
  ): RequestHandlerMiddleware[Nothing, Any, Nothing, Nothing] =
    new RequestHandlerMiddleware.Simple[Any, Nothing] {
      override def apply[Env, Err](handler: Handler[Env, Err, Request, Response])(implicit
        trace: Trace,
      ): Handler[Env, Err, Request, Response] =
        Handler.fromFunctionZIO[Request] { request =>
          val cookieContent = request
            .header(Header.Cookie)
            .flatMap { cookies =>
              cookies.value
                .filter(_.name == name)
                .headOption
            }
            .map(_.content)
          val headerContent = request.headers.get(name)

          cookieContent.zip(headerContent) match {
            case Some(cookie -> header) if cookie == header => handler.runZIO(request)
            case _                                          => ZIO.succeed(Response.status(status))
          }
        }

    }
}
