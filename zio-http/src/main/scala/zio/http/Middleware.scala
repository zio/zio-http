package zio.http

import zio.http.middleware.{HttpRouteMiddlewares, RequestHandlerMiddlewares}

object Middleware extends RequestHandlerMiddlewares with HttpRouteMiddlewares {
  def codec[BIn, AOut]: HandlerAspect.Codec[BIn, AOut] = HandlerAspect.codec[BIn, AOut]

  def codecHttp[BIn, AOut]: HandlerAspect.CodecHttp[BIn, AOut] = HandlerAspect.codecHttp[BIn, AOut]

  def codecZIO[BIn, AOut]: HandlerAspect.CodecZIO[BIn, AOut] = HandlerAspect.codecZIO[BIn, AOut]

  def transform[BIn, AOut]: HandlerAspect.Transform[BIn, AOut] = HandlerAspect.transform[BIn, AOut]
}
