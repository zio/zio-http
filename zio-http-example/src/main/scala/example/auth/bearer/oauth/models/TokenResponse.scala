package example.auth.bearer.oauth.models

import zio.json.{DeriveJsonCodec, JsonCodec}

case class TokenResponse(
  accessToken: String,
  tokenType: String,
  expiresIn: Long,
  refreshToken: String,
)
object TokenResponse {
  implicit val codec: JsonCodec[TokenResponse] = DeriveJsonCodec.gen
}
