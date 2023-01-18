package zio.http.middleware

import zio.http.{Http, HttpRouteMiddleware, Middleware, Request, Response}
import zio.{Trace, Unsafe, ZIO}

private[zio] trait HttpRouteMiddlewares extends Cors {

  /**
   * Creates a middleware which can allow or disallow access to an http based on
   * the predicate
   */
  def allow(condition: Request => Boolean): HttpRouteMiddleware[Any, Nothing] =
    Middleware.allow[Request, Response](condition)

  /**
   * Creates a middleware which can allow or disallow access to an http based on
   * the predicate effect
   */
  def allowZIO[R, Err](condition: Request => ZIO[R, Err, Boolean]): HttpRouteMiddleware[R, Err] =
    Middleware.allowZIO[Request, Response](condition)

  /**
   * Removes the trailing slash from the path.
   */
  def dropTrailingSlash: HttpRouteMiddleware[Any, Nothing] =
    new HttpRouteMiddleware[Any, Nothing] {
      override def apply[R1 <: Any, Err1 >: Nothing](
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

object HttpRouteMiddlewares extends HttpRouteMiddlewares
