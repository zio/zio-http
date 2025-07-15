package example.auth.webauthn
import Types._
import zio._

// ============================================================================
// WebAuthn Server
// ============================================================================

case class WebAuthnServer(
                           rpId: String,
                           rpName: String,
                           rpOrigin: String,
                           credentialStorage: CredentialStorage = new InMemoryCredentialStorage,
                         ) {

  private var pendingChallenges: Map[String, (BufferSource, Long)] = Map.empty

  def startRegistration(
                         userName: String,
                         userDisplayName: String,
                         userHandle: BufferSource,
                         authenticatorSelection: Option[AuthenticatorSelectionCriteria] = None,
                         attestation: AttestationConveyancePreference = AttestationConveyancePreference.None,
                       ): Task[PublicKeyCredentialCreationOptions] = {

    val challenge = generateChallenge()
    val sessionId = generateSessionId()

    // Store challenge for later verification
    pendingChallenges = pendingChallenges + (sessionId -> (challenge, java.lang.System.currentTimeMillis()))

    // Get existing credentials to exclude
    for {
      existingCreds <- credentialStorage.getCredentialsByRpAndUser(rpId, userHandle)
    } yield {
      val excludeCredentials = existingCreds.map { cred =>
        PublicKeyCredentialDescriptor(
          credentialType = cred.credentialType,
          id = cred.id,
        )
      }

      PublicKeyCredentialCreationOptions(
        rp = PublicKeyCredentialRpEntity(name = rpName, id = Some(rpId)),
        user = PublicKeyCredentialUserEntity(
          name = userName,
          id = userHandle,
          displayName = userDisplayName,
        ),
        challenge = challenge,
        pubKeyCredParams = Seq(
          PublicKeyCredentialParameters(PublicKeyCredentialType.PublicKey, -7),  // ES256
          PublicKeyCredentialParameters(PublicKeyCredentialType.PublicKey, -257), // RS256
        ),
        timeout = Some(300000), // 5 minutes
        excludeCredentials = excludeCredentials,
        authenticatorSelection = authenticatorSelection,
        attestation = attestation,
      )
    }
  }

  def finishRegistration(
                          sessionId: String,
                          credential: PublicKeyCredential,
                          userHandle: BufferSource,
                        ): Task[RelyingPartyOperations.RegistrationResult] = {

    for {
      // Verify challenge
      _ <- verifyChallenge(sessionId)

      // Get registration options from stored challenge
      (challenge, _) = pendingChallenges(sessionId)
      options        = createOptionsForChallenge(challenge, userHandle)

      // Verify the registration
      result <- RelyingPartyOperations.verifyRegistration(
        options,
        credential,
        rpOrigin,
        requireUserVerification = false,
      )

      // Store the credential
      credSource = PublicKeyCredentialSource(
        credentialType = PublicKeyCredentialType.PublicKey,
        id = result.credentialId,
        privateKey = Array.empty[Byte], // Server doesn't store private key
        rpId = rpId,
        userHandle = Some(userHandle),
      )
      _ <- credentialStorage.storeCredential(rpId, userHandle, credSource)

      // Clean up challenge
      _ = pendingChallenges = pendingChallenges - sessionId

    } yield result
  }

  def startAuthentication(
                           userHandle: Option[BufferSource] = None,
                         ): Task[(String, PublicKeyCredentialRequestOptions)] = {

    val challenge = generateChallenge()
    val sessionId = generateSessionId()

    // Store challenge for later verification
    pendingChallenges = pendingChallenges + (sessionId -> (challenge, java.lang.System.currentTimeMillis()))

    val allowCredentialsTask = userHandle match {
      case Some(handle) =>
        // User identified, get their credentials
        credentialStorage.getCredentialsByRpAndUser(rpId, handle).map { creds =>
          creds.map { cred =>
            PublicKeyCredentialDescriptor(
              credentialType = cred.credentialType,
              id = cred.id,
            )
          }
        }
      case None         =>
        // Usernameless authentication, let authenticator discover credentials
        ZIO.succeed(Seq.empty[PublicKeyCredentialDescriptor])
    }

    for {
      allowCredentials <- allowCredentialsTask
    } yield {
      val options = PublicKeyCredentialRequestOptions(
        challenge = challenge,
        timeout = Some(300000), // 5 minutes
        rpId = Some(rpId),
        allowCredentials = allowCredentials,
        userVerification = UserVerificationRequirement.Preferred,
      )

      (sessionId, options)
    }
  }

  def finishAuthentication(
                            sessionId: String,
                            credential: PublicKeyCredential,
                          ): Task[RelyingPartyOperations.AuthenticationResult] = {

    for {
      // Verify challenge
      _ <- verifyChallenge(sessionId)

      // Get the stored credential
      credSource <- credentialStorage
        .getCredentialById(credential.rawId)
        .someOrFail(
          new Exception("Credential not found"),
        )

      // Get stored sign count
      storage = credentialStorage.asInstanceOf[InMemoryCredentialStorage]
      storedSignCount <- storage.getSignCount(credential.rawId)

      // Get authentication options from stored challenge
      (challenge, _) = pendingChallenges(sessionId)
      options        = PublicKeyCredentialRequestOptions(
        challenge = challenge,
        rpId = Some(rpId),
      )

      // Verify the authentication
      result <- RelyingPartyOperations.verifyAuthentication(
        options,
        credential,
        rpOrigin,
        Array.empty[Byte], // Placeholder public key
        storedSignCount,
        requireUserVerification = false,
      )

      // Update sign count if successful
      _ <-
        if (result.success) {
          credentialStorage.updateSignCount(credential.rawId, result.signCount)
        } else {
          ZIO.unit
        }

      // Clean up challenge
      _ = pendingChallenges = pendingChallenges - sessionId

    } yield result
  }

  // Helper methods

  private def generateChallenge(): BufferSource = {
    val random    = new scala.util.Random()
    val challenge = new Array[Byte](32)
    random.nextBytes(challenge)
    challenge
  }

  private def generateSessionId(): String = {
    java.util.UUID.randomUUID().toString
  }

  private def verifyChallenge(sessionId: String): Task[Unit] = {
    pendingChallenges.get(sessionId) match {
      case Some((_, timestamp)) =>
        val age = java.lang.System.currentTimeMillis() - timestamp
        if (age > 300000) { // 5 minute timeout
          ZIO.fail(new Exception("Challenge expired"))
        } else {
          ZIO.unit
        }
      case None                 =>
        ZIO.fail(new Exception("Invalid session ID"))
    }
  }

  private def createOptionsForChallenge(
                                         challenge: BufferSource,
                                         userHandle: BufferSource,
                                       ): PublicKeyCredentialCreationOptions = {
    PublicKeyCredentialCreationOptions(
      rp = PublicKeyCredentialRpEntity(name = rpName, id = Some(rpId)),
      user = PublicKeyCredentialUserEntity(
        name = "user",
        id = userHandle,
        displayName = "User",
      ),
      challenge = challenge,
      pubKeyCredParams = Seq(
        PublicKeyCredentialParameters(PublicKeyCredentialType.PublicKey, -7),
      ),
    )
  }
}

