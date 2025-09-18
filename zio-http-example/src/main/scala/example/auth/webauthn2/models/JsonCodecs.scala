package example.auth.webauthn2.models

import zio.json._

/**
 * JSON codecs for WebAuthn DTOs
 */
object JsonCodecs {
  // Base64 URL encoding/decoding for byte arrays
  implicit val byteArrayEncoder: JsonEncoder[Array[Byte]] =
    JsonEncoder.string.contramap(java.util.Base64.getUrlEncoder.withoutPadding.encodeToString(_))

  implicit val byteArrayDecoder: JsonDecoder[Array[Byte]] =
    JsonDecoder.string.map(java.util.Base64.getUrlDecoder.decode(_))

  // Request decoders
  implicit val registrationStartRequestDecoder: JsonDecoder[RegistrationStartRequest] = DeriveJsonDecoder.gen
  implicit val attestationResponseDecoder: JsonDecoder[AttestationResponse] = DeriveJsonDecoder.gen
//  implicit val registrationFinishRequestDecoder: JsonDecoder[RegistrationFinishRequest] = DeriveJsonDecoder.gen

  implicit val authenticationStartRequestDecoder: JsonDecoder[AuthenticationStartRequest] = DeriveJsonDecoder.gen
  implicit val assertionResponseDecoder: JsonDecoder[AssertionResponse] = DeriveJsonDecoder.gen
//  implicit val authenticationFinishRequestDecoder: JsonDecoder[AuthenticationFinishRequest] = DeriveJsonDecoder.gen

  implicit val clientDataDecoder: JsonDecoder[ClientData] = DeriveJsonDecoder.gen

  implicit val allowedCredentialEncoder: JsonEncoder[AllowedCredential] = DeriveJsonEncoder.gen
}