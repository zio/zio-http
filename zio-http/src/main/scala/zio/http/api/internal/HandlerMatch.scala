package zio.http.api.internal

import zio.Chunk
import zio.http.api.Service
import zio.stacktracer.TracingImplicits.disableAutoTrace // scalafix:ok;

final case class HandlerMatch[MI, MO, -R, +E, I, O](
  handledApi: Service.HandledAPI[MI, MO, R, E, I, O, _],
  routeInputs: Chunk[Any],
)
