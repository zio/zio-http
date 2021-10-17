package zhttp.experiment

import zhttp.http.{HttpApp, Request, Response}
import zio.ZIO

/**
 * Middlewares for HttpApp.
 */
final case class HttpMiddleware[-R, +E](
  middleware: Middleware[R, E, Request, Response[Any, Nothing], Request, Response[R, E]],
) { self => }

object HttpMiddleware {
  val identity: HttpMiddleware[Any, Nothing] = HttpMiddleware(Middleware.identity)

  def transform[R, E](f: HttpApp[Any, Nothing] => HttpApp[R, E]): HttpMiddleware[R, E] =
    HttpMiddleware(Middleware.transform(app => f(HttpApp(app)).asHttp))

  def transformM[R, E](f: HttpApp[Any, Nothing] => ZIO[R, E, HttpApp[R, E]]): HttpMiddleware[R, E] =
    HttpMiddleware(Middleware.transformM(app => f(HttpApp(app)).map(_.asHttp)))
}
