package example.auth.bearer.jwt.refresh.core

import zio.json._

case class TokenResponse(
  accessToken: String,
  refreshToken: String,
  tokenType: String = "Bearer",
  expiresIn: Int = 300,
)

object TokenResponse {
  implicit val codec: JsonCodec[TokenResponse] = DeriveJsonCodec.gen
}
