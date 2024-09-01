package test.component

import zio.schema._
import zio.schema.annotation.fieldName
import zio.schema.annotation.validate
import zio.schema.validation.Validation
import java.util.UUID

case class Order(
  @fieldName("1st item") firstItem: String,
  @fieldName("2nd item") secondItem: Option[String],
  @fieldName("3rd item") thirdItem: Option[String],
  @fieldName("PRODUCT_NAME") productNAME: String,
  id: UUID,
  @fieldName("num-of-items")
  @validate[Int](Validation.greaterThan(0))
  numOfItems: Int,
  @fieldName("price in dollars")
  @validate[Double](Validation.greaterThan(-1.0))
  priceInDollars: Double,
)
object Order {
  implicit val codec: Schema[Order] = DeriveSchema.gen[Order]
}