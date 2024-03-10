package test.component

import zio.schema._
import zio.Chunk

case class UserNameArray(
  id: Int,
  name: Chunk[String],
)
object UserNameArray {

  implicit val codec: Schema[UserNameArray] = DeriveSchema.gen[UserNameArray]

}
