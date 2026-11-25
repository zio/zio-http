package zio.http.gen.model

import zio.Chunk

import zio.schema._

case class UserNameArray(id: Int, name: Chunk[String])
object UserNameArray {
  implicit val codec: Schema[UserNameArray] = DeriveSchema.gen[UserNameArray]
}
