package zio.http.api.internal

import zio.Chunk
import zio.http.api.Service

final case class HandlerMatch[-R, +E, I, O](handledApi: Service.HandledAPI[R, E, I, O], routeInputs: Chunk[Any])
