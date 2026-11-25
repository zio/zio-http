package zio.http.gen.model

import zio.schema._

case class User(id: Int, name: String)
object User {
  implicit val codec: Schema[User] = DeriveSchema.gen[User]
}
