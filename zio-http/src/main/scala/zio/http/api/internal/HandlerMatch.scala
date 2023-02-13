package zio.http.api.internal

import zio.Chunk
import zio.http.api._
import zio.stacktracer.TracingImplicits.disableAutoTrace // scalafix:ok;

final case class HandlerMatch[-R, +E, I, O, M <: EndpointMiddleware](
  handledApi: Routes.Single[R, _ <: E, I, O, M],
)
