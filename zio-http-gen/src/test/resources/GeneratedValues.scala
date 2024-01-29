package test.component

import zio.schema._

case class Values(
  value1: Int,
  value2: String,
)
object Values {

  implicit val codec: Schema[Values] = DeriveSchema.gen[Values]

}
