package example.jsonAPI

import zio.json.{DeriveJsonCodec, JsonCodec}

import java.util.UUID

final case class User(name: String, age: Int, id: User.Id)

object User {
  type Id = UUID

  implicit val codec: JsonCodec[User] = DeriveJsonCodec.gen[User]
}
