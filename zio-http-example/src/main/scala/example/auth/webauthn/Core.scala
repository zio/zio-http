package example.auth.webauthn
import example.auth.webauthn.Types._

case class PublicKeyCredentialParameters(
  credentialType: PublicKeyCredentialType,
  alg: COSEAlgorithmIdentifier,
)

trait PublicKeyCredentialEntity {
  def name: DOMString
}

case class PublicKeyCredentialRpEntity(
  name: DOMString,
  id: Option[DOMString] = None,
) extends PublicKeyCredentialEntity

case class PublicKeyCredentialUserEntity(
  name: DOMString,
  id: BufferSource,
  displayName: DOMString,
) extends PublicKeyCredentialEntity

case class PublicKeyCredentialDescriptor(
  credentialType: PublicKeyCredentialType,
  id: BufferSource,
  transports: Option[Seq[AuthenticatorTransport]] = None,
)

case class AuthenticatorSelectionCriteria(
  authenticatorAttachment: Option[AuthenticatorAttachment] = None,
  residentKey: Option[ResidentKeyRequirement] = None,
  requireResidentKey: Boolean = false,
  userVerification: UserVerificationRequirement = UserVerificationRequirement.Preferred,
)

case class TokenBinding(
  status: TokenBindingStatus,
  id: Option[DOMString] = None,
)

case class CollectedClientData(
  clientDataType: DOMString,
  challenge: DOMString,
  origin: DOMString,
  crossOrigin: Option[Boolean] = None,
  tokenBinding: Option[TokenBinding] = None,
)
