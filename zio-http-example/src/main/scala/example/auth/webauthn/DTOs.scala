package example.auth.webauthn

import zio._
import zio.schema._

case class StartRegistrationRequest(
  username: String,
  displayName: String,
  userVerification: Option[String] = None,
  authenticatorAttachment: Option[String] = None,
  residentKey: Option[String] = None,
)

object StartRegistrationRequest {
  implicit val schema: Schema[StartRegistrationRequest] = DeriveSchema.gen
}

case class StartRegistrationResponse(
  sessionId: String,
  options: PublicKeyCredentialCreationOptionsDTO,
)

object StartRegistrationResponse {
  implicit val schema: Schema[StartRegistrationResponse] = DeriveSchema.gen
}

case class FinishRegistrationRequest(
  sessionId: String,
  credential: PublicKeyCredentialDTO,
)

object FinishRegistrationRequest {
  implicit val schema: Schema[FinishRegistrationRequest] = DeriveSchema.gen
}

case class FinishRegistrationResponse(
  success: Boolean,
  credentialId: String,
  message: String,
)

object FinishRegistrationResponse {
  implicit val schema: Schema[FinishRegistrationResponse] = DeriveSchema.gen
}

case class StartAuthenticationRequest(
  username: Option[String] = None,
  userVerification: Option[String] = None,
)

object StartAuthenticationRequest {
  implicit val codec: Schema[StartAuthenticationRequest] = DeriveSchema.gen
}

case class StartAuthenticationResponse(
  sessionId: String,
  options: PublicKeyCredentialRequestOptionsDTO,
)

object StartAuthenticationResponse {
  implicit val codec: Schema[StartAuthenticationResponse] = DeriveSchema.gen
}

case class FinishAuthenticationRequest(
  sessionId: String,
  credential: PublicKeyCredentialDTO,
)

object FinishAuthenticationRequest {
  implicit val schema: Schema[FinishAuthenticationRequest] = DeriveSchema.gen
}

case class FinishAuthenticationResponse(
  success: Boolean,
  username: Option[String],
  message: String,
)

object FinishAuthenticationResponse {
  implicit val schema: Schema[FinishAuthenticationResponse] = DeriveSchema.gen
}

case class PublicKeyCredentialCreationOptionsDTO(
  rp: PublicKeyCredentialRpEntityDTO,
  user: PublicKeyCredentialUserEntityDTO,
  challenge: String, // Base64URL encoded
  pubKeyCredParams: Chunk[PublicKeyCredentialParametersDTO],
  timeout: Option[Long],
  excludeCredentials: Chunk[PublicKeyCredentialDescriptorDTO],
  authenticatorSelection: Option[AuthenticatorSelectionCriteriaDTO],
  attestation: String,
  extensions: Option[Map[String, String]],
)

case class PublicKeyCredentialRpEntityDTO(
  name: String,
  id: Option[String],
)

case class PublicKeyCredentialUserEntityDTO(
  name: String,
  id: String, // Base64URL encoded
  displayName: String,
)

case class PublicKeyCredentialParametersDTO(
  `type`: String,
  alg: Long,
)

object PublicKeyCredentialParametersDTO {
  implicit val schema: Schema[PublicKeyCredentialParametersDTO] = DeriveSchema.gen
}

case class PublicKeyCredentialDescriptorDTO(
  `type`: String,
  id: String, // Base64URL encoded
  transports: Option[Chunk[String]],
)

case class AuthenticatorSelectionCriteriaDTO(
  authenticatorAttachment: Option[String],
  residentKey: Option[String],
  requireResidentKey: Option[Boolean],
  userVerification: String,
)

case class PublicKeyCredentialRequestOptionsDTO(
  challenge: String, // Base64URL encoded
  timeout: Option[Long],
  rpId: Option[String],
  allowCredentials: Chunk[PublicKeyCredentialDescriptorDTO],
  userVerification: String,
  extensions: Option[Map[String, String]],
)

case class PublicKeyCredentialDTO(
  id: String,
  rawId: String, // Base64URL encoded
  response: AuthenticatorResponseDTO,
  authenticatorAttachment: Option[String],
  clientExtensionResults: Option[Map[String, String]],
)

case class AuthenticatorResponseDTO(
  clientDataJSON: String,            // Base64URL encoded
  attestationObject: Option[String], // Base64URL encoded (for registration)
  authenticatorData: Option[String], // Base64URL encoded (for authentication)
  signature: Option[String],         // Base64URL encoded (for authentication)
  userHandle: Option[String],        // Base64URL encoded (for authentication)
)
