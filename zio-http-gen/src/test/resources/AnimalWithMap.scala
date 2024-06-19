package test.component

import zio.schema._

case class Animals(
  total: Int,
  counts: Map[String, Int],
)
object Animals {

  implicit val codec: Schema[Animals] = DeriveSchema.gen[Animals]

}
