package example.auth.webauthn

sealed trait PublicKeyCredentialType
object PublicKeyCredentialType {
  case object PublicKey extends PublicKeyCredentialType

  def fromString(s: String): Option[PublicKeyCredentialType] = s match {
    case "public-key" => Some(PublicKey)
    case _            => None
  }
}

sealed trait AuthenticatorAttachment
object AuthenticatorAttachment {
  case object Platform      extends AuthenticatorAttachment
  case object CrossPlatform extends AuthenticatorAttachment

  def fromString(s: String): Option[AuthenticatorAttachment] = s match {
    case "platform"       => Some(Platform)
    case "cross-platform" => Some(CrossPlatform)
    case _                => None
  }
}

sealed trait ResidentKeyRequirement
object ResidentKeyRequirement {
  case object Discouraged extends ResidentKeyRequirement
  case object Preferred   extends ResidentKeyRequirement
  case object Required    extends ResidentKeyRequirement

  def fromString(s: String): Option[ResidentKeyRequirement] = s match {
    case "discouraged" => Some(Discouraged)
    case "preferred"   => Some(Preferred)
    case "required"    => Some(Required)
    case _             => None
  }
}

sealed trait UserVerificationRequirement
object UserVerificationRequirement {
  case object Required    extends UserVerificationRequirement
  case object Preferred   extends UserVerificationRequirement
  case object Discouraged extends UserVerificationRequirement

  def fromString(s: String): Option[UserVerificationRequirement] = s match {
    case "required"    => Some(Required)
    case "preferred"   => Some(Preferred)
    case "discouraged" => Some(Discouraged)
    case _             => None
  }
}

sealed trait AttestationConveyancePreference
object AttestationConveyancePreference {
  case object None       extends AttestationConveyancePreference
  case object Indirect   extends AttestationConveyancePreference
  case object Direct     extends AttestationConveyancePreference
  case object Enterprise extends AttestationConveyancePreference

  def fromString(s: String): Option[AttestationConveyancePreference] = s match {
    case "none"       => Some(None)
    case "indirect"   => Some(Indirect)
    case "direct"     => Some(Direct)
    case "enterprise" => Some(Enterprise)
    case _            => Option.empty
  }
}

sealed trait AuthenticatorTransport
object AuthenticatorTransport {
  case object USB      extends AuthenticatorTransport
  case object NFC      extends AuthenticatorTransport
  case object BLE      extends AuthenticatorTransport
  case object Internal extends AuthenticatorTransport

  def fromString(s: String): Option[AuthenticatorTransport] = s match {
    case "usb"      => Some(USB)
    case "nfc"      => Some(NFC)
    case "ble"      => Some(BLE)
    case "internal" => Some(Internal)
    case _          => None
  }
}

sealed trait TokenBindingStatus
object TokenBindingStatus {
  case object Present   extends TokenBindingStatus
  case object Supported extends TokenBindingStatus

  def fromString(s: String): Option[TokenBindingStatus] = s match {
    case "present"   => Some(Present)
    case "supported" => Some(Supported)
    case _           => None
  }
}
