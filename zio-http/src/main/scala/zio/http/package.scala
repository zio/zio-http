package zio

import zio.http.middleware.IT
import zio.http.socket.WebSocketChannelEvent
import zio.stacktracer.TracingImplicits.disableAutoTrace // scalafix:ok;

package object http extends PathSyntax with RequestSyntax with RouteDecoderModule {
  type HttpApp[-R, +E] = Http[R, E, Request, Response]
  type UHttpApp        = HttpApp[Any, Nothing]
  type RHttpApp[-R]    = HttpApp[R, Throwable]
  type EHttpApp        = HttpApp[Any, Throwable]
  type UHttp[-A, +B]   = Http[Any, Nothing, A, B]

  type ResponseZIO[-R, +E]                                    = ZIO[R, E, Response]
  type UMiddleware[+AIn, -BIn, -AOut, +BOut, AInT <: IT[AIn]] =
    Middleware[Any, Nothing, AIn, BIn, AOut, BOut, AInT]

  type Client = ZClient[Any, Body, Throwable, Response]
  def Client: ZClient.type = ZClient
}
