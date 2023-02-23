package zio

import zio.stacktracer.TracingImplicits.disableAutoTrace // scalafix:ok;

package object http extends PathSyntax with RequestSyntax with RouteDecoderModule {

  type RequestHandler[-R, +Err] = Handler[R, Err, Request, Response]

  type HttpAppMiddleware[-R, +Err] = Middleware[Nothing, R, Err, Any, Request, Response, Request, Response]

  type RequestHandlerMiddleware[-R, +Err] =
    HandlerMiddleware[Nothing, R, Err, Any, Request, Response, Request, Response]

  type UMiddleware[+AIn, -AOut, -BIn, +BOut] = Middleware[Nothing, Any, Nothing, Any, AIn, AOut, BIn, BOut]

  type HttpApp[-R, +Err] = Http[R, Err, Request, Response]
  type UHttpApp          = HttpApp[Any, Nothing]
  type RHttpApp[-R]      = HttpApp[R, Throwable]
  type EHttpApp          = HttpApp[Any, Throwable]
  type UHttp[-A, +B]     = Http[Any, Nothing, A, B]
  type App[-R]           = HttpApp[R, Response]

  type Client = ZClient[Any, Body, Throwable, Response]
  def Client: ZClient.type = ZClient
}
