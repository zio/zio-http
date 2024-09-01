package test.component

import zio.schema._

case class Animals(
  counts: Map[String, Int],
  total: Int,
)
object Animals {
  implicit val codec: Schema[Animals] = DeriveSchema.gen[Animals]
}
