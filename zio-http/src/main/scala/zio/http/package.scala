package zio

import zio.stacktracer.TracingImplicits.disableAutoTrace

package object http extends PathSyntax with RequestSyntax with RouteDecoderModule {

  type RequestHandler[-R, +Err]           = Handler[R, Err, Request, Response]
  type RequestHandlerMiddleware[-R, +Err] = HandlerMiddleware[R, Err, Request, Response, Request, Response]

  type HttpApp[-R, +Err] = Http[R, Err, Request, Response]
  type UHttpApp          = HttpApp[Any, Nothing]
  type RHttpApp[-R]      = HttpApp[R, Throwable]
  type EHttpApp          = HttpApp[Any, Throwable]
  type UHttp[-A, +B]     = Http[Any, Nothing, A, B]
  type App[-R]           = HttpApp[R, Response]

  type UMiddleware[+AIn, -AOut, -BIn, +BOut] = Middleware[Any, Nothing, AIn, AOut, BIn, BOut]
  type HttpAppMiddleware[-R, +Err]           = Middleware[R, Err, Request, Response, Request, Response]

  type Client = ZClient[Any, Body, Throwable, Response]
  def Client: ZClient.type = ZClient
}
