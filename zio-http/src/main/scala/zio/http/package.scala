package zio

import zio.stacktracer.TracingImplicits.disableAutoTrace // scalafix:ok;

package object http extends PathSyntax with RequestSyntax with RouteDecoderModule {

  type RequestHandler[-R, +Err] = Handler[R, Err, Request, Response]
  type AppHandler[-R]           = RequestHandler[R, Response]

  type RequestHandlerMiddleware[-R, +Err] = HandlerMiddleware[R, Err, Request, Response, Request, Response]
  type AppHandlerMiddleware[-R]           = RequestHandlerMiddleware[R, Response]

  type HttpRoute[-R, +Err] = Http[R, Err, Request, Response]
  type App[-R]             = HttpRoute[R, Response]

  type HttpRouteMiddleware[-R, +Err] = Middleware[R, Err, Request, Response, Request, Response]
  type AppMiddleware[-R]             = HttpRouteMiddleware[R, Response]

  type Client = ZClient[Any, Body, Throwable, Response]
  def Client: ZClient.type = ZClient
}
