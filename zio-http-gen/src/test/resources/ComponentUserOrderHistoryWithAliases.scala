package test.component

import zio.schema._

case class UserOrderHistory(
  history: Map[OrderId.Type, Order],
  user_id: UserId.Type,
)
object UserOrderHistory {
  implicit val codec: Schema[UserOrderHistory] = DeriveSchema.gen[UserOrderHistory]
}