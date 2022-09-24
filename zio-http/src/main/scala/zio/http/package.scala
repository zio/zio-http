package zio

import zio.stacktracer.TracingImplicits.disableAutoTrace // scalafix:ok;

package object http extends PathSyntax with RequestSyntax with RouteDecoderModule {
  type HttpApp[-R, +E]                       = Http[R, E, Request, Response]
  type UHttpApp                              = HttpApp[Any, Nothing]
  type RHttpApp[-R]                          = HttpApp[R, Throwable]
  type UHttp[-A, +B]                         = Http[Any, Nothing, A, B]
  type ResponseZIO[-R, +E]                   = ZIO[R, E, Response]
  type UMiddleware[+AIn, -BIn, -AOut, +BOut] = Middleware[Any, Nothing, AIn, BIn, AOut, BOut]

  type Client = ZClient[Any, Body, Throwable, Response]
  def Client: ZClient.type = ZClient
}
