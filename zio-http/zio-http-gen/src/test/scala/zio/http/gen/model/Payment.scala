package zio.http.gen.model

import zio.schema._
import zio.schema.annotation._

sealed trait Payment
object Payment {
  case class Card(number: String, cvv: String) extends Payment
  @caseName("cash")
  case class Cash(amount: Int)                 extends Payment

  implicit val codec: Schema[Payment] = DeriveSchema.gen[Payment]
}
