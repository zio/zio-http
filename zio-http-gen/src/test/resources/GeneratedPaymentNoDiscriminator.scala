package test.component

import zio.schema._
import zio.schema.annotation._

@noDiscriminator
sealed trait PaymentNoDiscriminator
object PaymentNoDiscriminator {

  implicit val codec: Schema[PaymentNoDiscriminator] = DeriveSchema.gen[PaymentNoDiscriminator]
  case class Card(
    number: String,
    cvv: String,
  )
  object Card {

    implicit val codec: Schema[Card] = DeriveSchema.gen[Card]

  }
  case class Cash(
    amount: Int,
  )
  object Cash {

    implicit val codec: Schema[Cash] = DeriveSchema.gen[Cash]

  }
}
