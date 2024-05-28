package test.component

import zio.schema._
import zio.schema.annotation._

@discriminatorName("type")
sealed trait PaymentNamedDiscriminator
object PaymentNamedDiscriminator {

  implicit val codec: Schema[PaymentNamedDiscriminator] = DeriveSchema.gen[PaymentNamedDiscriminator]
  @caseName("Card")
  case class Card(
    number: String,
    cvv: String,
  ) extends PaymentNamedDiscriminator
  object Card {

    implicit val codec: Schema[Card] = DeriveSchema.gen[Card]

  }
  @caseName("cash")
  case class Cash(
    amount: Int,
  ) extends PaymentNamedDiscriminator
  object Cash {

    implicit val codec: Schema[Cash] = DeriveSchema.gen[Cash]

  }
}
