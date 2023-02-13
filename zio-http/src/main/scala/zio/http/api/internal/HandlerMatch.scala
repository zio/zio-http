package zio.http.api.internal

import zio.Chunk
import zio.http.api.Endpoints

final case class HandlerMatch[-R, +E, I, O](
  handledApi: Endpoints.HandledEndpoint[R, E, I, O, _],
  routeInputs: Chunk[Any],
)
