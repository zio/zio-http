package zio.http.api

import zio.ZIO

final case class HandledAPI[-R, +E, In0, Out](
  api: API[In0, Out],
  handler: In0 => ZIO[R, E, Out],
)
