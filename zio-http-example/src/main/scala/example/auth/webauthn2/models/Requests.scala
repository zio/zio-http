package example.auth.webauthn2.models

/**
 * Request DTOs for WebAuthn operations
 */

// Registration request DTOs
case class RegistrationStartRequest(username: String)

case class RegistrationFinishRequest(
  username: String,
  id: String,
  rawId: String,
  response: AttestationResponse,
)

case class AttestationResponse(
  clientDataJSON: String,
  attestationObject: String,
)

// Authentication request DTOs
case class AuthenticationStartRequest(username: Option[String])

case class AuthenticationFinishRequest(
  username: Option[String], // Optional for discoverable passkeys
  id: String,
  rawId: String,
  response: AssertionResponse,
)

case class AssertionResponse(
  clientDataJSON: String,
  authenticatorData: String,
  signature: String,
  userHandle: Option[String],
)

// Client data structure for parsing authentication responses
case class ClientData(challenge: String, origin: String, `type`: String)
