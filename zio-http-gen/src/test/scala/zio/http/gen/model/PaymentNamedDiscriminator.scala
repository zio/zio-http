package zio.http.gen.model

import zio.schema._
import zio.schema.annotation._

@discriminatorName("type")
sealed trait PaymentNamedDiscriminator
object PaymentNamedDiscriminator {
  case class Card(number: String, cvv: String) extends PaymentNamedDiscriminator
  @caseName("cash")
  case class Cash(amount: Int)                 extends PaymentNamedDiscriminator

  implicit val codec: Schema[PaymentNamedDiscriminator] = DeriveSchema.gen[PaymentNamedDiscriminator]
}
