package zio.http.gen.model

import zio.schema._

sealed trait Direction
object Direction {
  case object North extends Direction
  case object South extends Direction
  case object East  extends Direction
  case object West  extends Direction

  implicit val codec: Schema[Direction] = DeriveSchema.gen[Direction]
}
