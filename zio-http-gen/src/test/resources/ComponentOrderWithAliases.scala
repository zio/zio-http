package test.component

import zio.schema._

case class Order(
  id: OrderId.Type,
  product: String,
  @zio.schema.annotation.validate[Int](zio.schema.validation.Validation.greaterThan(0)) quantity: Int,
  @zio.schema.annotation.validate[Double](zio.schema.validation.Validation.greaterThan(-1.0)) price: Double,
)
object Order {
  implicit val codec: Schema[Order] = DeriveSchema.gen[Order]
}
