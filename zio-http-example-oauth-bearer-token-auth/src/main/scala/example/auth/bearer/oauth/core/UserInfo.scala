package example.auth.bearer.oauth.core

import zio.json._

case class UserInfo(
  @jsonField("sub")
  username: String,
  email: String,
  roles: Set[String],
)

object UserInfo {
  implicit val codec: JsonCodec[UserInfo] = DeriveJsonCodec.gen
}
