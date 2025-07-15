package example.auth.webauthn
import example.auth.webauthn.Types._
import zio._

import java.nio.ByteBuffer
import java.security.MessageDigest

// ============================================================================
// Credential Creation Options
// ============================================================================

case class PublicKeyCredentialCreationOptions(
  rp: PublicKeyCredentialRpEntity,
  user: PublicKeyCredentialUserEntity,
  challenge: BufferSource,
  pubKeyCredParams: Seq[PublicKeyCredentialParameters],
  timeout: Option[Long] = None,
  excludeCredentials: Seq[PublicKeyCredentialDescriptor] = Seq.empty,
  authenticatorSelection: Option[AuthenticatorSelectionCriteria] = None,
  attestation: AttestationConveyancePreference = AttestationConveyancePreference.None,
  extensions: Option[AuthenticationExtensionsClientInputs] = None,
)

// ============================================================================
// Credential Request Options
// ============================================================================

case class PublicKeyCredentialRequestOptions(
  challenge: BufferSource,
  timeout: Option[Long] = None,
  rpId: Option[USVString] = None,
  allowCredentials: Seq[PublicKeyCredentialDescriptor] = Seq.empty,
  userVerification: UserVerificationRequirement = UserVerificationRequirement.Preferred,
  extensions: Option[AuthenticationExtensionsClientInputs] = None,
)

// ============================================================================
// Extension Support
// ============================================================================

case class AuthenticationExtensionsClientInputs(
  appid: Option[USVString] = None,
  appidExclude: Option[USVString] = None,
  uvm: Option[Boolean] = None,
  credProps: Option[Boolean] = None,
  largeBlob: Option[AuthenticationExtensionsLargeBlobInputs] = None,
)

case class AuthenticationExtensionsClientOutputs(
  appid: Option[Boolean] = None,
  appidExclude: Option[Boolean] = None,
  uvm: Option[Seq[UvmEntry]] = None,
  credProps: Option[CredentialPropertiesOutput] = None,
  largeBlob: Option[AuthenticationExtensionsLargeBlobOutputs] = None,
)

case class AuthenticationExtensionsLargeBlobInputs(
  support: Option[String] = None,
  read: Option[Boolean] = None,
  write: Option[BufferSource] = None,
)

case class AuthenticationExtensionsLargeBlobOutputs(
  supported: Option[Boolean] = None,
  blob: Option[BufferSource] = None,
  written: Option[Boolean] = None,
)

case class CredentialPropertiesOutput(
  rk: Option[Boolean] = None,
)

// ============================================================================
// Authenticator Responses
// ============================================================================

trait AuthenticatorResponse {
  def clientDataJSON: BufferSource
}

case class AuthenticatorAttestationResponse(
  clientDataJSON: BufferSource,
  attestationObject: BufferSource,
  transports: Seq[AuthenticatorTransport] = Seq.empty,
) extends AuthenticatorResponse {

  def getTransports(): Seq[DOMString] = transports.map {
    case AuthenticatorTransport.USB      => "usb"
    case AuthenticatorTransport.NFC      => "nfc"
    case AuthenticatorTransport.BLE      => "ble"
    case AuthenticatorTransport.Internal => "internal"
  }
}

case class AuthenticatorAssertionResponse(
  clientDataJSON: BufferSource,
  authenticatorData: BufferSource,
  signature: BufferSource,
  userHandle: Option[BufferSource] = None,
) extends AuthenticatorResponse

// ============================================================================
// Public Key Credential
// ============================================================================

case class PublicKeyCredential(
  id: DOMString,
  rawId: BufferSource,
  response: AuthenticatorResponse,
  authenticatorAttachment: Option[AuthenticatorAttachment] = None,
  clientExtensionResults: AuthenticationExtensionsClientOutputs = AuthenticationExtensionsClientOutputs(),
) {
  def getClientExtensionResults(): AuthenticationExtensionsClientOutputs = clientExtensionResults
}

// ============================================================================
// Authenticator Data
// ============================================================================

case class AuthenticatorData(
  rpIdHash: BufferSource,
  flags: AuthenticatorDataFlags,
  signCount: Long,
  attestedCredentialData: Option[AttestedCredentialData] = None,
  extensions: Option[BufferSource] = None,
) {

  def toBytes: BufferSource = {
    val buffer = ByteBuffer.allocate(calculateSize())

    // RP ID hash (32 bytes)
    buffer.put(rpIdHash)

    // Flags (1 byte)
    buffer.put(flags.toByte)

    // Sign count (4 bytes, big-endian)
    buffer.putInt(signCount.toInt)

    // Attested credential data (variable length)
    attestedCredentialData.foreach { acd =>
      buffer.put(acd.toBytes)
    }

    // Extensions (variable length)
    extensions.foreach(buffer.put)

    buffer.array()
  }

  private def calculateSize(): Int = {
    37 + // Base size (32 + 1 + 4)
      attestedCredentialData.map(_.size).getOrElse(0) +
      extensions.map(_.length).getOrElse(0)
  }
}

case class AuthenticatorDataFlags(
  userPresent: Boolean = false,
  userVerified: Boolean = false,
  attestedCredentialDataIncluded: Boolean = false,
  extensionDataIncluded: Boolean = false,
) {
  def toByte: Byte = {
    var flags: Int = 0
    if (userPresent) flags |= 0x01
    if (userVerified) flags |= 0x04
    if (attestedCredentialDataIncluded) flags |= 0x40
    if (extensionDataIncluded) flags |= 0x80
    flags.toByte
  }
}

case class AttestedCredentialData(
  aaguid: BufferSource,    // 16 bytes
  credentialIdLength: Int, // 2 bytes
  credentialId: BufferSource,
  credentialPublicKey: BufferSource,
) {
  def toBytes: BufferSource = {
    val buffer = ByteBuffer.allocate(size)
    buffer.put(aaguid)
    buffer.putShort(credentialIdLength.toShort)
    buffer.put(credentialId)
    buffer.put(credentialPublicKey)
    buffer.array()
  }

  def size: Int = 16 + 2 + credentialId.length + credentialPublicKey.length
}

// ============================================================================
// Credential Source
// ============================================================================

case class PublicKeyCredentialSource(
  credentialType: PublicKeyCredentialType,
  id: BufferSource,
  privateKey: BufferSource,
  rpId: DOMString,
  userHandle: Option[BufferSource],
  otherUI: Option[Map[String, Any]] = None,
)

// ============================================================================
// Relying Party Operations
// ============================================================================

object RelyingPartyOperations {

  case class RegistrationResult(
    credentialId: BufferSource,
    credentialPublicKey: BufferSource,
    signCount: Long,
    attestationType: AttestationType,
    trustPath: Seq[BufferSource] = Seq.empty,
  )

  case class AuthenticationResult(
    credentialId: BufferSource,
    userHandle: Option[BufferSource],
    signCount: Long,
    success: Boolean,
  )

  sealed trait AttestationType
  object AttestationType {
    case object None   extends AttestationType
    case object Self   extends AttestationType
    case object Basic  extends AttestationType
    case object AttCA  extends AttestationType
    case object AnonCA extends AttestationType
  }

  case class RegistrationVerificationError(message: String)   extends Exception(message)
  case class AuthenticationVerificationError(message: String) extends Exception(message)

  /**
   * Verify a registration ceremony response
   */
  def verifyRegistration(
    options: PublicKeyCredentialCreationOptions,
    credential: PublicKeyCredential,
    expectedOrigin: String,
    requireUserVerification: Boolean = false,
  ): IO[Throwable, RegistrationResult] = {

    credential.response match {
      case attestationResponse: AuthenticatorAttestationResponse =>
        ZIO.attempt {
          // Step 1-4: Parse and verify client data
          val clientData = parseClientData(attestationResponse.clientDataJSON)
          verifyClientData(clientData, options.challenge, expectedOrigin, "webauthn.create")

          // Step 5: Compute client data hash
          val clientDataHash = computeHash(attestationResponse.clientDataJSON)

          // Step 6-8: Parse authenticator data and verify RP ID
          val authData = parseAuthenticatorData(attestationResponse.clientDataJSON)
          verifyRpId(authData.rpIdHash, extractRpId(options.rp, expectedOrigin))

          // Step 9: Verify user presence
          if (!authData.flags.userPresent) {
            throw RegistrationVerificationError("User presence flag not set")
          }

          // Step 10: Verify user verification if required
          if (requireUserVerification && !authData.flags.userVerified) {
            throw RegistrationVerificationError("User verification required but not performed")
          }

          // Step 11: Verify algorithm matches requested
          val attestedCredData = authData.attestedCredentialData.getOrElse {
            throw RegistrationVerificationError("Attested credential data missing")
          }

          // For now, return a basic result (full attestation verification would require CBOR parsing)
          RegistrationResult(
            credentialId = attestedCredData.credentialId,
            credentialPublicKey = attestedCredData.credentialPublicKey,
            signCount = authData.signCount,
            attestationType = AttestationType.None, // Simplified
          )
        }

      case _ => ZIO.fail(RegistrationVerificationError("Invalid response type for registration"))
    }
  }

  /**
   * Verify an authentication ceremony response
   */
  def verifyAuthentication(
    options: PublicKeyCredentialRequestOptions,
    credential: PublicKeyCredential,
    expectedOrigin: String,
    credentialPublicKey: BufferSource,
    storedSignCount: Long,
    requireUserVerification: Boolean = false,
  ): IO[Throwable, AuthenticationResult] = {

    credential.response match {
      case assertionResponse: AuthenticatorAssertionResponse =>
        ZIO.attempt {
          // Step 1-4: Parse and verify client data
          val clientData = parseClientData(assertionResponse.clientDataJSON)
          verifyClientData(clientData, options.challenge, expectedOrigin, "webauthn.get")

          // Step 5: Compute client data hash
          val clientDataHash = computeHash(assertionResponse.clientDataJSON)

          // Step 6-7: Parse authenticator data and verify RP ID
          val authData     = parseAuthenticatorData(assertionResponse.authenticatorData)
          val expectedRpId = options.rpId.getOrElse(extractDomainFromOrigin(expectedOrigin))
          verifyRpId(authData.rpIdHash, expectedRpId)

          // Step 8: Verify user presence
          if (!authData.flags.userPresent) {
            throw AuthenticationVerificationError("User presence flag not set")
          }

          // Step 9: Verify user verification if required
          if (requireUserVerification && !authData.flags.userVerified) {
            throw AuthenticationVerificationError("User verification required but not performed")
          }

          // Step 10-11: Verify signature (simplified - would need actual crypto verification)
          val signatureValid = verifySignature(
            assertionResponse.authenticatorData ++ clientDataHash,
            assertionResponse.signature,
            credentialPublicKey,
          )

          if (!signatureValid) {
            throw AuthenticationVerificationError("Signature verification failed")
          }

          // Step 12: Verify signature counter
          val signCountValid = authData.signCount > storedSignCount || (authData.signCount == 0 && storedSignCount == 0)

          AuthenticationResult(
            credentialId = credential.rawId,
            userHandle = assertionResponse.userHandle,
            signCount = authData.signCount,
            success = signCountValid,
          )
        }

      case _ => ZIO.fail(AuthenticationVerificationError("Invalid response type for authentication"))
    }
  }

  // Helper methods

  private def parseClientData(clientDataJSON: BufferSource): CollectedClientData = {
    // In a real implementation, this would parse JSON
    // For now, return a placeholder
    CollectedClientData("webauthn.create", "", "")
  }

  private def verifyClientData(
    clientData: CollectedClientData,
    expectedChallenge: BufferSource,
    expectedOrigin: String,
    expectedType: String,
  ): Unit = {
    if (clientData.clientDataType != expectedType) {
      throw new Exception(s"Invalid client data type: expected $expectedType")
    }
    // Additional verification would be implemented here
  }

  private def computeHash(data: BufferSource): BufferSource = {
    MessageDigest.getInstance("SHA-256").digest(data)
  }

  private def parseAuthenticatorData(authData: BufferSource): AuthenticatorData = {
    val buffer = ByteBuffer.wrap(authData)

    // Parse RP ID hash (32 bytes)
    val rpIdHash = new Array[Byte](32)
    buffer.get(rpIdHash)

    // Parse flags (1 byte)
    val flagsByte = buffer.get()
    val flags     = AuthenticatorDataFlags(
      userPresent = (flagsByte & 0x01) != 0,
      userVerified = (flagsByte & 0x04) != 0,
      attestedCredentialDataIncluded = (flagsByte & 0x40) != 0,
      extensionDataIncluded = (flagsByte & 0x80) != 0,
    )

    // Parse sign count (4 bytes)
    val signCount = buffer.getInt().toLong

    // Parse attested credential data if present
    val attestedCredData = if (flags.attestedCredentialDataIncluded) {
      val aaguid = new Array[Byte](16)
      buffer.get(aaguid)

      val credIdLength = buffer.getShort() & 0xffff
      val credId       = new Array[Byte](credIdLength)
      buffer.get(credId)

      // Remaining bytes are the public key (simplified parsing)
      val pubKeyLength = buffer.remaining()
      val pubKey       = new Array[Byte](pubKeyLength)
      buffer.get(pubKey)

      Some(AttestedCredentialData(aaguid, credIdLength, credId, pubKey))
    } else {
      None
    }

    AuthenticatorData(rpIdHash, flags, signCount, attestedCredData)
  }

  private def verifyRpId(rpIdHash: BufferSource, expectedRpId: String): Unit = {
    val expectedHash = computeHash(expectedRpId.getBytes("UTF-8"))
    if (!rpIdHash.sameElements(expectedHash)) {
      throw new Exception("RP ID hash verification failed")
    }
  }

  private def extractRpId(rp: PublicKeyCredentialRpEntity, origin: String): String = {
    rp.id.getOrElse(extractDomainFromOrigin(origin))
  }

  private def extractDomainFromOrigin(origin: String): String = {
    // Simplified domain extraction
    origin.replaceFirst("https?://", "").split(":")(0)
  }

  private def verifySignature(data: BufferSource, signature: BufferSource, publicKey: BufferSource): Boolean = {
    // In a real implementation, this would perform actual cryptographic verification
    // For now, return true as a placeholder
    true
  }
}

// ============================================================================
// Attestation Statement Formats
// ============================================================================

object AttestationFormats {

  sealed trait AttestationStatementFormat {
    def identifier: String
  }

  case object PackedAttestationFormat extends AttestationStatementFormat {
    val identifier = "packed"
  }

  case object TPMAttestationFormat extends AttestationStatementFormat {
    val identifier = "tpm"
  }

  case object AndroidKeyAttestationFormat extends AttestationStatementFormat {
    val identifier = "android-key"
  }

  case object AndroidSafetyNetAttestationFormat extends AttestationStatementFormat {
    val identifier = "android-safetynet"
  }

  case object FidoU2FAttestationFormat extends AttestationStatementFormat {
    val identifier = "fido-u2f"
  }

  case object NoneAttestationFormat extends AttestationStatementFormat {
    val identifier = "none"
  }

  case object AppleAttestationFormat extends AttestationStatementFormat {
    val identifier = "apple"
  }
}
