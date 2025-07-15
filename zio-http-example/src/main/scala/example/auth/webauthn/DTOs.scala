package example.auth.webauthn

import zio.json._
import zio.json.ast.Json
import zio.schema.{DeriveSchema, Schema}

// Request/Response DTOs
case class StartRegistrationRequest(
  username: String,
  displayName: String,
  userVerification: Option[String] = None,
  authenticatorAttachment: Option[String] = None,
  residentKey: Option[String] = None,
)

object StartRegistrationRequest {
  implicit val encoder: JsonEncoder[StartRegistrationRequest] = DeriveJsonEncoder.gen
  implicit val decoder: JsonDecoder[StartRegistrationRequest] = DeriveJsonDecoder.gen
  implicit val schema: Schema[StartRegistrationRequest] = DeriveSchema.gen
}

case class StartRegistrationResponse(
  sessionId: String,
  options: PublicKeyCredentialCreationOptionsDTO,
)

object StartRegistrationResponse {
  implicit val encoder: JsonEncoder[StartRegistrationResponse] = DeriveJsonEncoder.gen
  implicit val decoder: JsonDecoder[StartRegistrationResponse] = DeriveJsonDecoder.gen
}

case class FinishRegistrationRequest(
  sessionId: String,
  credential: PublicKeyCredentialDTO,
)

object FinishRegistrationRequest {
  implicit val encoder: JsonEncoder[FinishRegistrationRequest] = DeriveJsonEncoder.gen
  implicit val decoder: JsonDecoder[FinishRegistrationRequest] = DeriveJsonDecoder.gen
  implicit val schema: Schema[FinishRegistrationRequest] = DeriveSchema.gen[FinishRegistrationRequest]
}

case class FinishRegistrationResponse(
  success: Boolean,
  credentialId: String,
  message: String,
)

object FinishRegistrationResponse {
  implicit val encoder: JsonEncoder[FinishRegistrationResponse] = DeriveJsonEncoder.gen
  implicit val decoder: JsonDecoder[FinishRegistrationResponse] = DeriveJsonDecoder.gen
}

case class StartAuthenticationRequest(
  username: Option[String] = None,
  userVerification: Option[String] = None,
)

object StartAuthenticationRequest {
  implicit val encoder: JsonEncoder[StartAuthenticationRequest] = DeriveJsonEncoder.gen
  implicit val decoder: JsonDecoder[StartAuthenticationRequest] = DeriveJsonDecoder.gen
  implicit val codec: Schema[StartAuthenticationRequest] = zio.schema.DeriveSchema.gen[StartAuthenticationRequest]
}

case class StartAuthenticationResponse(
  sessionId: String,
  options: PublicKeyCredentialRequestOptionsDTO,
)

object StartAuthenticationResponse {
  implicit val encoder: JsonEncoder[StartAuthenticationResponse] = DeriveJsonEncoder.gen
  implicit val decoder: JsonDecoder[StartAuthenticationResponse] = DeriveJsonDecoder.gen
}

case class FinishAuthenticationRequest(
  sessionId: String,
  credential: PublicKeyCredentialDTO,
)

object FinishAuthenticationRequest {
  implicit val encoder: JsonEncoder[FinishAuthenticationRequest] = DeriveJsonEncoder.gen
  implicit val decoder: JsonDecoder[FinishAuthenticationRequest] = DeriveJsonDecoder.gen
  implicit val schema: Schema[FinishAuthenticationRequest] = DeriveSchema.gen
}

case class FinishAuthenticationResponse(
  success: Boolean,
  username: Option[String],
  message: String,
)

object FinishAuthenticationResponse {
  implicit val encoder: JsonEncoder[FinishAuthenticationResponse] = DeriveJsonEncoder.gen
  implicit val decoder: JsonDecoder[FinishAuthenticationResponse] = DeriveJsonDecoder.gen
  implicit val schema: Schema[FinishAuthenticationResponse] = DeriveSchema.gen
}

case class MobileDeviceInfo(
  deviceId: String,
  deviceName: String,
  platform: String,
  capabilities: Set[String],
  lastSeen: Long,
)

object MobileDeviceInfo {
  implicit val encoder: JsonEncoder[MobileDeviceInfo] = DeriveJsonEncoder.gen
  implicit val decoder: JsonDecoder[MobileDeviceInfo] = DeriveJsonDecoder.gen
}

case class MobileKeyExchangeRequest(
  deviceId: String,
  publicKey: String,
  challenge: String,
  signature: String,
)

object MobileKeyExchangeRequest {
  implicit val encoder: JsonEncoder[MobileKeyExchangeRequest] = DeriveJsonEncoder.gen
  implicit val decoder: JsonDecoder[MobileKeyExchangeRequest] = DeriveJsonDecoder.gen
}

case class MobileKeyExchangeResponse(
  success: Boolean,
  sessionKey: Option[String],
  message: String,
)

object MobileKeyExchangeResponse {
  implicit val encoder: JsonEncoder[MobileKeyExchangeResponse] = DeriveJsonEncoder.gen
  implicit val decoder: JsonDecoder[MobileKeyExchangeResponse] = DeriveJsonDecoder.gen
}

// DTO classes for JSON serialization
case class PublicKeyCredentialCreationOptionsDTO(
  rp: PublicKeyCredentialRpEntityDTO,
  user: PublicKeyCredentialUserEntityDTO,
  challenge: String, // Base64URL encoded
  pubKeyCredParams: Seq[PublicKeyCredentialParametersDTO],
  timeout: Option[Long],
  excludeCredentials: Seq[PublicKeyCredentialDescriptorDTO],
  authenticatorSelection: Option[AuthenticatorSelectionCriteriaDTO],
  attestation: String,
  extensions: Option[Map[String, Json]],
)

object PublicKeyCredentialCreationOptionsDTO {
  implicit val encoder: JsonEncoder[PublicKeyCredentialCreationOptionsDTO] = DeriveJsonEncoder.gen
  implicit val decoder: JsonDecoder[PublicKeyCredentialCreationOptionsDTO] = DeriveJsonDecoder.gen
}

case class PublicKeyCredentialRpEntityDTO(
  name: String,
  id: Option[String],
)

object PublicKeyCredentialRpEntityDTO {
  implicit val encoder: JsonEncoder[PublicKeyCredentialRpEntityDTO] = DeriveJsonEncoder.gen
  implicit val decoder: JsonDecoder[PublicKeyCredentialRpEntityDTO] = DeriveJsonDecoder.gen
}

case class PublicKeyCredentialUserEntityDTO(
  name: String,
  id: String, // Base64URL encoded
  displayName: String,
)

object PublicKeyCredentialUserEntityDTO {
  implicit val encoder: JsonEncoder[PublicKeyCredentialUserEntityDTO] = DeriveJsonEncoder.gen
  implicit val decoder: JsonDecoder[PublicKeyCredentialUserEntityDTO] = DeriveJsonDecoder.gen
}

case class PublicKeyCredentialParametersDTO(
  `type`: String,
  alg: Long,
)

object PublicKeyCredentialParametersDTO {
  implicit val encoder: JsonEncoder[PublicKeyCredentialParametersDTO] = DeriveJsonEncoder.gen
  implicit val decoder: JsonDecoder[PublicKeyCredentialParametersDTO] = DeriveJsonDecoder.gen
}

case class PublicKeyCredentialDescriptorDTO(
  `type`: String,
  id: String, // Base64URL encoded
  transports: Option[Seq[String]],
)

object PublicKeyCredentialDescriptorDTO {
  implicit val encoder: JsonEncoder[PublicKeyCredentialDescriptorDTO] = DeriveJsonEncoder.gen
  implicit val decoder: JsonDecoder[PublicKeyCredentialDescriptorDTO] = DeriveJsonDecoder.gen
}

case class AuthenticatorSelectionCriteriaDTO(
  authenticatorAttachment: Option[String],
  residentKey: Option[String],
  requireResidentKey: Option[Boolean],
  userVerification: String,
)

object AuthenticatorSelectionCriteriaDTO {
  implicit val encoder: JsonEncoder[AuthenticatorSelectionCriteriaDTO] = DeriveJsonEncoder.gen
  implicit val decoder: JsonDecoder[AuthenticatorSelectionCriteriaDTO] = DeriveJsonDecoder.gen
}

case class PublicKeyCredentialRequestOptionsDTO(
  challenge: String, // Base64URL encoded
  timeout: Option[Long],
  rpId: Option[String],
  allowCredentials: Seq[PublicKeyCredentialDescriptorDTO],
  userVerification: String,
  extensions: Option[Map[String, Json]],
)

object PublicKeyCredentialRequestOptionsDTO {
  implicit val encoder: JsonEncoder[PublicKeyCredentialRequestOptionsDTO] = DeriveJsonEncoder.gen
  implicit val decoder: JsonDecoder[PublicKeyCredentialRequestOptionsDTO] = DeriveJsonDecoder.gen
}

case class PublicKeyCredentialDTO(
  id: String,
  rawId: String, // Base64URL encoded
  response: AuthenticatorResponseDTO,
  authenticatorAttachment: Option[String],
  clientExtensionResults: Option[Map[String, String]],
)

object PublicKeyCredentialDTO {
  implicit val encoder: JsonEncoder[PublicKeyCredentialDTO] = DeriveJsonEncoder.gen
  implicit val decoder: JsonDecoder[PublicKeyCredentialDTO] = DeriveJsonDecoder.gen
}

case class AuthenticatorResponseDTO(
  clientDataJSON: String,            // Base64URL encoded
  attestationObject: Option[String], // Base64URL encoded (for registration)
  authenticatorData: Option[String], // Base64URL encoded (for authentication)
  signature: Option[String],         // Base64URL encoded (for authentication)
  userHandle: Option[String],        // Base64URL encoded (for authentication)
)

object AuthenticatorResponseDTO {
  implicit val encoder: JsonEncoder[AuthenticatorResponseDTO] = DeriveJsonEncoder.gen
  implicit val decoder: JsonDecoder[AuthenticatorResponseDTO] = DeriveJsonDecoder.gen
}
