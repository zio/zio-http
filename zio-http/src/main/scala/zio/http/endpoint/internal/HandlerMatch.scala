package zio.http.endpoint.internal

import zio.Chunk
import zio.http.endpoint._
import zio.stacktracer.TracingImplicits.disableAutoTrace // scalafix:ok;

final case class HandlerMatch[-R, +E, I, O, M <: EndpointMiddleware](
  handledApi: Routes.Single[R, _ <: E, I, O, M],
)
