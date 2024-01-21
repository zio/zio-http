package test.component

import zio._
import zio.schema._

case class UserNameArray(
  id: Int,
  name: Chunk[String],
)
object UserNameArray {

  implicit val codec: Schema[UserNameArray] = DeriveSchema.gen[UserNameArray]

}
