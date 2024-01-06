package test.component

import zio.schema._
import zio.schema.annotation._

sealed trait Payment
object Payment {

  implicit val codec: Schema[Payment] = DeriveSchema.gen[Payment]
  @caseName("Card")
  case class Card(
    number: String,
    cvv: String,
  )
  object Card {

    implicit val codec: Schema[Card] = DeriveSchema.gen[Card]

  }
  @caseName("cash")
  case class Cash(
    amount: Int,
  )
  object Cash {

    implicit val codec: Schema[Cash] = DeriveSchema.gen[Cash]

  }
}
