package test.component

import zio.schema._
import java.util.UUID

case class UserOrderHistory(
  history: Map[UUID, Order],
  user_id: UUID,
)
object UserOrderHistory {
  implicit val codec: Schema[UserOrderHistory] = DeriveSchema.gen[UserOrderHistory]
}