package zio

import zio.stacktracer.TracingImplicits.disableAutoTrace // scalafix:ok;

package object http extends PathSyntax with RequestSyntax with RouteDecoderModule {

  type RequestHandler[-R, -Ctx, +Err]     = Handler[R, Ctx, Err, Request, Response]
  type RequestHandlerMiddleware[-R, +Err] = HandlerMiddleware[R, Any, Err, Request, Response, Request, Response]

  type HttpApp[-R, -Ctx, +Err] = Http[R, Ctx, Err, Request, Response]
  type UHttpApp                = HttpApp[Any, Any, Nothing]
  type RHttpApp[-R]            = HttpApp[R, Any, Throwable]
  type EHttpApp                = HttpApp[Any, Any, Throwable]
  type UHttp[-A, +B]           = Http[Any, Any, Nothing, A, B]
  type App[-R]                 = HttpApp[R, Any, Response]

  type UMiddleware[+AIn, -AOut, -BIn, +BOut] = Middleware[Any, Nothing, AIn, AOut, BIn, BOut]
  type HttpAppMiddleware[-R, +Err]           = Middleware[R, Err, Request, Response, Request, Response]

  type Client = ZClient[Any, Body, Throwable, Response]
  def Client: ZClient.type = ZClient
}
