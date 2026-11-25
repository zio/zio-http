package zio.http.gen.model

import zio.schema._
import zio.schema.annotation._

@noDiscriminator
sealed trait PaymentNoDiscriminator
object PaymentNoDiscriminator {
  case class Card(number: String, cvv: String) extends PaymentNoDiscriminator
  @caseName("cash")
  case class Cash(amount: Int)                 extends PaymentNoDiscriminator

  implicit val codec: Schema[PaymentNoDiscriminator] = DeriveSchema.gen[PaymentNoDiscriminator]
}
