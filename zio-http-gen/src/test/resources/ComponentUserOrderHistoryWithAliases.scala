package test.component

import zio.schema._

case class UserOrderHistory(
  user_id: UserId.Type,
  history: Map[OrderId.Type, Order],
)
object UserOrderHistory {
  implicit val codec: Schema[UserOrderHistory] = DeriveSchema.gen[UserOrderHistory]
}
