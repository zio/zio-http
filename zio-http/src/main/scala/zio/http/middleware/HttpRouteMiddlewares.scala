package zio.http.middleware

import zio.http.{Http, HttpAppMiddleware, Request, Response}
import zio.{Trace, ZIO}
import zio.stacktracer.TracingImplicits.disableAutoTrace // scalafix:ok;

private[zio] trait HttpRoutesMiddlewares extends Cors {

  /**
   * Creates a middleware which can allow or disallow access to an http based on
   * the predicate
   */
  def allow(
    condition: Request => Boolean,
  ): HttpAppMiddleware[Nothing, Any, Any, Nothing] =
    HttpAppMiddleware.allow(condition)

  /**
   * Creates a middleware which can allow or disallow access to an http based on
   * the predicate effect
   */
  def allowZIO[R, Err](
    condition: Request => ZIO[R, Err, Boolean],
  ): HttpAppMiddleware[Nothing, R, Err, Nothing] =
    HttpAppMiddleware.allowZIO(condition)

  /**
   * Removes the trailing slash from the path.
   */
  def dropTrailingSlash: HttpAppMiddleware[Nothing, Any, Nothing, Any] =
    new HttpAppMiddleware.Simple[Nothing, Any, Nothing, Any] {
      override def apply[R1, Err1](
        http: Http[R1, Err1, Request, Response],
      )(implicit trace: Trace): Http[R1, Err1, Request, Response] =
        Http.fromHandlerZIO[Request] { request =>
          if (request.url.queryParams.isEmpty)
            http.runHandler(request.dropTrailingSlash)
          else
            http.runHandler(request)
        }
    }
}

object HttpRoutesMiddlewares extends HttpRoutesMiddlewares
