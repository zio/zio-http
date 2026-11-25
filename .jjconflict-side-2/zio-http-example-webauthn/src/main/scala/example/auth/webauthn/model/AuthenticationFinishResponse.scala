package example.auth.webauthn.model

import zio.json.{DeriveJsonCodec, JsonCodec}

case class AuthenticationFinishResponse(
  success: Boolean,
  username: String,
)

object AuthenticationFinishResponse {
  implicit val codec: JsonCodec[AuthenticationFinishResponse] = DeriveJsonCodec.gen
}
