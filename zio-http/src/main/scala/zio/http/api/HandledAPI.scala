package zio.http.api

import zio.ZIO

final case class HandledAPI[-R, +E, In0, Out0](
  api: API[In0, Out0],
  handler: In0 => ZIO[R, E, Out0],
)
