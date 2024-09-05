package test.component

import zio.schema._
import zio.schema.annotation.validate
import zio.schema.validation.Validation

case class Order(
  id: OrderId.Type,
  product: String,
  @validate[Int](Validation.greaterThan(0)) quantity: Int,
  @validate[Double](Validation.greaterThan(-1.0)) price: Double,
)
object Order {
  implicit val codec: Schema[Order] = DeriveSchema.gen[Order]
}
