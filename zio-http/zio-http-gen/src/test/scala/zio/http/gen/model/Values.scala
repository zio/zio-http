package zio.http.gen.model

import zio.schema.{DeriveSchema, Schema}

case class Values(value1: Int, value2: String)
object Values {
  implicit val codec: Schema[Values] = DeriveSchema.gen[Values]
}
