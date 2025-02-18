package test.component

import zio.schema._
import zio.schema.annotation.fieldName
import zio.schema.annotation.validate
import zio.schema.validation.Validation
import java.util.UUID

case class Order(
  @fieldName("ORDER-ID") orderID: UUID,
  @fieldName("price_in dollars")
  @validate[Double](Validation.greaterThan(-1.0))
  priceInDollars: Double,
  @fieldName("1st item") firstItem: String,
  @fieldName("2nd item") secondItem: Option[String],
)
object Order {
  implicit val codec: Schema[Order] = DeriveSchema.gen[Order]
}
