package test.component

import zio.schema._

case class ValidatedData(
  @zio.schema.annotation.validate[String](zio.schema.validation.Validation.minLength(10)) name: String,
  @zio.schema.annotation.validate[Int](
    zio.schema.validation.Validation.greaterThan(0) && zio.schema.validation.Validation.lessThan(100),
  ) age: Int,
)
object ValidatedData {
  implicit val codec: Schema[ValidatedData] = DeriveSchema.gen[ValidatedData]
}
