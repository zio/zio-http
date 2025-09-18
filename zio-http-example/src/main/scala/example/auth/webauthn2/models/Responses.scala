package example.auth.webauthn2.models

import zio.json.{DeriveJsonCodec, JsonCodec}

case class RegistrationFinishResponse(
  success: Boolean,
  credentialId: String,
  message: String,
)
object RegistrationFinishResponse {
  implicit val codec: JsonCodec[RegistrationFinishResponse] = DeriveJsonCodec.gen
}

// Authentication response DTOs
//case class AuthenticationStartResponse(
//  challenge: String,
//  rpId: String,
//  allowCredentials: List[AllowedCredential],
//  userVerification: String,
//  timeout: Long,
//)

case class AuthenticationFinishResponse(
  success: Boolean,
  username: String,
)
object AuthenticationFinishResponse {
  implicit val codec: JsonCodec[AuthenticationFinishResponse] = DeriveJsonCodec.gen
}

case class AllowedCredential(`type`: String, id: String)
