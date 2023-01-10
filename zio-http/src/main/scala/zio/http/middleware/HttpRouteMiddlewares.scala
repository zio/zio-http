package zio.http.middleware

import zio.http.{HttpRouteMiddleware, Request, Response, Route, RouteAspect}
import zio.{Trace, Unsafe, ZIO}

private[zio] trait HttpRouteMiddlewares extends Cors {
  def allow(condition: Request => Boolean): HttpRouteMiddleware[Any, Nothing] =
    RouteAspect.allow(condition)

  def allowZIO[R, Err](condition: Request => ZIO[R, Err, Boolean]): HttpRouteMiddleware[R, Err] =
    RouteAspect.allowZIO(condition)

  def dropTrailingSlash: HttpRouteMiddleware[Any, Nothing] =
    new HttpRouteMiddleware[Any, Nothing] {
      override def apply[R1 <: Any, Err1 >: Nothing](
        route: Route[R1, Err1, Request, Response],
      )(implicit trace: Trace): Route[R1, Err1, Request, Response] =
        Route.fromHandlerHExit[Request] { request =>
          if (request.url.queryParams.isEmpty)
            route.toHandlerOrNull(request.dropTrailingSlash)(Unsafe.unsafe)
          else
            route.toHandlerOrNull(request)(Unsafe.unsafe)
        }
    }
}

object HttpRouteMiddlewares extends HttpRouteMiddlewares
