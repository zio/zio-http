package example.auth.bearer.oauth.core

import zio.json._

case class TokenResponse(
  accessToken: String,
  refreshToken: String,
  tokenType: String = "Bearer",
  expiresIn: Long = 300L,
)

object TokenResponse {
  implicit val codec: JsonCodec[TokenResponse] = DeriveJsonCodec.gen
}
