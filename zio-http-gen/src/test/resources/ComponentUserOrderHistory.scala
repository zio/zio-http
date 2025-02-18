package test.component

import zio.schema._
import java.util.UUID

case class UserOrderHistory(
  user_id: UUID,
  history: Map[UUID, Order],
)
object UserOrderHistory {
  implicit val codec: Schema[UserOrderHistory] = DeriveSchema.gen[UserOrderHistory]
}
