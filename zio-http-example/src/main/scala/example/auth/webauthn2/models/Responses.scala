package example.auth.webauthn2.models

import com.yubico.webauthn.data.PublicKeyCredentialCreationOptions

/**
 * Response DTOs for WebAuthn operations
 */
case class RpInfo(id: String, name: String)

case class UserInfo(id: String, name: String, displayName: String)

case class CredParam(`type`: String, alg: Int)

case class AuthSelection(
  authenticatorAttachment: Option[String],
  requireResidentKey: Boolean,
  residentKey: String,
  userVerification: String,
)

// Authentication response DTOs
case class AuthenticationStartResponse(
  challenge: String,
  rpId: String,
  allowCredentials: List[AllowedCredential],
  userVerification: String,
  timeout: Long,
)

case class AllowedCredential(`type`: String, id: String)
