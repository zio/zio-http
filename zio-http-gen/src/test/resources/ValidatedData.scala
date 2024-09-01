package test.component

import zio.schema._
import zio.schema.annotation.validate
import zio.schema.validation.Validation

case class ValidatedData(
  @validate[Int](Validation.greaterThan(0) && Validation.lessThan(100)) age: Int,
  @validate[String](Validation.minLength(10)) name: String,
)
object ValidatedData {
  implicit val codec: Schema[ValidatedData] = DeriveSchema.gen[ValidatedData]
}
