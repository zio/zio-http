package example.auth.webauthn.models

import zio.json.{DeriveJsonCodec, JsonCodec}

case class RegistrationFinishResponse(
  success: Boolean,
  credentialId: String,
)

object RegistrationFinishResponse {
  implicit val codec: JsonCodec[RegistrationFinishResponse] = DeriveJsonCodec.gen
}

case class AuthenticationFinishResponse(
  success: Boolean,
  username: String,
)
object AuthenticationFinishResponse {
  implicit val codec: JsonCodec[AuthenticationFinishResponse] = DeriveJsonCodec.gen
}

