package example.auth.bearer.oauth.core

import zio.json._

case class Token(
  accessToken: String,
  refreshToken: String,
  tokenType: String = "Bearer",
  expiresIn: Long = 300L,
)

object Token {
  implicit val codec: JsonCodec[Token] = DeriveJsonCodec.gen
}
