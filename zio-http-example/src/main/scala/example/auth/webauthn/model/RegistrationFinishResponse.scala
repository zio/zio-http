package example.auth.webauthn.model

import zio.json.{DeriveJsonCodec, JsonCodec}

case class RegistrationFinishResponse(
  success: Boolean,
  credentialId: String,
)

object RegistrationFinishResponse {
  implicit val codec: JsonCodec[RegistrationFinishResponse] = DeriveJsonCodec.gen
}
