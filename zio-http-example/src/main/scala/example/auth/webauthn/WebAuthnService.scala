package example.auth.webauthn

import example.auth.webauthn.Types.Base64Url
import zio._
import scala.util.Random

// ============================================================================
// WebAuthn Service - Fixed with Proper JSON Error Responses
// ============================================================================

trait WebAuthnService {
  def startRegistration(request: StartRegistrationRequest): IO[String, StartRegistrationResponse]
  def finishRegistration(request: FinishRegistrationRequest): IO[String, FinishRegistrationResponse]
  def startAuthentication(request: StartAuthenticationRequest): IO[String, StartAuthenticationResponse]
  def finishAuthentication(request: FinishAuthenticationRequest): IO[String, FinishAuthenticationResponse]
}

case class WebAuthnServiceLive(
  webAuthnServer: WebAuthnServer,
  sessions: Ref[Map[String, (String, Long)]], // sessionId -> (username, timestamp)
  registeredUsers: Ref[Map[String, String]], // credentialId -> username
) extends WebAuthnService {

  def startRegistration(request: StartRegistrationRequest): IO[String, StartRegistrationResponse] =
    for {
      _ <- ZIO.debug("Starting registration process...")
      userHandle <- ZIO.succeed(generateUserHandle())
      sessionId = java.util.UUID.randomUUID().toString

      _ <- ZIO.logInfo(s"Starting registration for user: ${request.username}, sessionId: $sessionId")

      _ <- sessions.update(_ + (sessionId -> (request.username, java.lang.System.currentTimeMillis())))

      authenticatorSelection = createAuthenticatorSelection(request)
      attestation            = parseAttestationPreference(request.userVerification)

      // Start registration with WebAuthn server
      options <-
        webAuthnServer.startRegistration(
          request.username,
          request.displayName,
          userHandle,
          authenticatorSelection,
          attestation,
        ).mapError(e => s"WebAuthn server error: ${e.getMessage}")

      optionsDTO = convertToCreationOptionsDTO(options)
      response   = StartRegistrationResponse(sessionId, optionsDTO)

      _ <- ZIO.logInfo(s"Registration started successfully, returning sessionId: $sessionId")
    } yield response

  def finishRegistration(request: FinishRegistrationRequest): IO[String, FinishRegistrationResponse] =
    (for {
      _ <- ZIO.logInfo(s"Finishing registration with sessionId: ${request.sessionId}")

      currentSessions <- sessions.get
      _ <- ZIO.logInfo(s"Current sessions: ${currentSessions.keys}")

      sessionData <- ZIO.fromOption(currentSessions.get(request.sessionId))
        .orElseFail("Invalid session ID")

      (username, timestamp) = sessionData
      _ <- ZIO.logInfo(s"Found session for user: $username")

      currentTime = java.lang.System.currentTimeMillis()
      _ <- ZIO
        .fail("Session expired")
        .when(currentTime - timestamp > 300000) // 5 minutes

      // Store the credential ID to username mapping
      credentialId = request.credential.id
      _ <- registeredUsers.update(_ + (credentialId -> username))
      _ <- ZIO.logInfo(s"Stored credential mapping: $credentialId -> $username")

      // Clean up session
      _ <- sessions.update(_ - request.sessionId)
      _ <- ZIO.logInfo(s"Registration completed for user: $username")

      response = FinishRegistrationResponse(
        success = true,
        credentialId = credentialId,
        username = username,
        message = s"Registration successful for user: $username",
      )

    } yield response).catchAll { error =>
      // Return a proper JSON error response
      ZIO.succeed(FinishRegistrationResponse(
        success = false,
        credentialId = "",
        username = "",
        message = error
      ))
    }

  def startAuthentication(request: StartAuthenticationRequest): IO[String, StartAuthenticationResponse] =
    for {
      _ <- ZIO.unit
      sessionId = java.util.UUID.randomUUID().toString
      // Store username if provided for later retrieval
      _ <- sessions.update(_ + (sessionId -> (request.username.getOrElse(""), java.lang.System.currentTimeMillis())))

      // Parse userVerification from request
      userVerification = UserVerificationRequirement
        .fromString(request.userVerification.getOrElse("preferred"))
        .getOrElse(UserVerificationRequirement.Preferred)

      // Start authentication with WebAuthn server
      tuple <-
        webAuthnServer.startAuthentication(
          request.username.map(_ => generateUserHandle()),
          userVerification
        ).mapError(e => s"WebAuthn server error: ${e.getMessage}")

      (_, options) = tuple
      optionsDTO   = convertToRequestOptionsDTO(options)
      response     = StartAuthenticationResponse(sessionId, optionsDTO)
    } yield response

  def finishAuthentication(request: FinishAuthenticationRequest): IO[String, FinishAuthenticationResponse] =
    (for {
      _ <- ZIO.logInfo(s"Finishing authentication with sessionId: ${request.sessionId}")

      currentSessions <- sessions.get

      sessionData <- ZIO.fromOption(currentSessions.get(request.sessionId))
        .orElseFail("Invalid session ID")

      (_, timestamp) = sessionData

      currentTime = java.lang.System.currentTimeMillis()
      _ <- ZIO
        .fail("Session expired")
        .when(currentTime - timestamp > 300000) // 5 minutes

      // CRITICAL FIX: Verify the credential actually exists and belongs to a registered user
      credentialId = request.credential.id
      _ <- ZIO.logInfo(s"Looking up credential: $credentialId")

      // Get all registered users and check if this credential exists
      users <- registeredUsers.get
      _ <- ZIO.logInfo(s"Registered credentials: ${users.keys.mkString(", ")}")

      // Find the username associated with this credential
      username <- ZIO.fromOption(users.get(credentialId))
        .orElseFail(s"Unknown credential. This passkey is not registered with this application.")

      _ <- ZIO.logInfo(s"Found registered user for credential: $username")

      // Clean up session
      _ <- sessions.update(_ - request.sessionId)
      _ <- ZIO.logInfo(s"Authentication successful for user: $username")

      response = FinishAuthenticationResponse(
        success = true,
        username = Some(username),
        message = s"Authentication successful for user: $username",
      )

    } yield response).catchAll { error =>
      // Return a proper JSON error response instead of throwing
      ZIO.succeed(FinishAuthenticationResponse(
        success = false,
        username = None,
        message = error
      ))
    }

  // Helper methods
  private def generateUserHandle(): Array[Byte] = Random.nextBytes(32)

  private def createAuthenticatorSelection(request: StartRegistrationRequest): Option[AuthenticatorSelectionCriteria] =
    Some(
      AuthenticatorSelectionCriteria(
        authenticatorAttachment = request.authenticatorAttachment.flatMap(AuthenticatorAttachment.fromString),
        residentKey = request.residentKey.flatMap(ResidentKeyRequirement.fromString),
        userVerification = UserVerificationRequirement
          .fromString(request.userVerification.getOrElse("preferred"))
          .getOrElse(UserVerificationRequirement.Preferred),
      ),
    )

  private def parseAttestationPreference(userVerification: Option[String]): AttestationConveyancePreference =
    AttestationConveyancePreference.None

  private def convertToCreationOptionsDTO(
    options: PublicKeyCredentialCreationOptions,
  ): PublicKeyCredentialCreationOptionsDTO =
    PublicKeyCredentialCreationOptionsDTO(
      rp = PublicKeyCredentialRpEntityDTO(options.rp.name, options.rp.id),
      user = PublicKeyCredentialUserEntityDTO(
        options.user.name,
        Base64Url.encode(options.user.id),
        options.user.displayName,
      ),
      challenge = Base64Url.encode(options.challenge),
      pubKeyCredParams = Chunk.fromIterable(options.pubKeyCredParams.map(p => PublicKeyCredentialParametersDTO("public-key", p.alg))),
      timeout = options.timeout,
      excludeCredentials = Chunk.fromIterable(options.excludeCredentials.map(convertToDescriptorDTO)),
      authenticatorSelection = options.authenticatorSelection.map(convertToAuthSelectionDTO),
      attestation = "none",
      extensions = None,
    )

  private def convertToRequestOptionsDTO(
    options: PublicKeyCredentialRequestOptions,
  ): PublicKeyCredentialRequestOptionsDTO =
    PublicKeyCredentialRequestOptionsDTO(
      challenge = Base64Url.encode(options.challenge),
      timeout = options.timeout,
      rpId = options.rpId,
      allowCredentials = Chunk.fromIterable(options.allowCredentials.map(convertToDescriptorDTO)),
      userVerification = options.userVerification match {
        case UserVerificationRequirement.Required => "required"
        case UserVerificationRequirement.Preferred => "preferred"
        case UserVerificationRequirement.Discouraged => "discouraged"
      },
      extensions = None,
    )

  private def convertToDescriptorDTO(descriptor: PublicKeyCredentialDescriptor): PublicKeyCredentialDescriptorDTO =
    PublicKeyCredentialDescriptorDTO(
      `type` = "public-key",
      id = Base64Url.encode(descriptor.id),
      transports = descriptor.transports.map(_.map(_.toString)).map(Chunk.fromIterable(_)),
    )

  private def convertToAuthSelectionDTO(selection: AuthenticatorSelectionCriteria): AuthenticatorSelectionCriteriaDTO =
    AuthenticatorSelectionCriteriaDTO(
      authenticatorAttachment = selection.authenticatorAttachment.map(_.toString),
      residentKey = selection.residentKey.map(_.toString),
      requireResidentKey = Some(selection.requireResidentKey),
      userVerification = selection.userVerification.toString,
    )

  private def convertFromCredentialDTO(dto: PublicKeyCredentialDTO): PublicKeyCredential = {
    val response = dto.response.attestationObject match {
      case Some(attestationObject) =>
        AuthenticatorAttestationResponse(
          clientDataJSON = Base64Url.decode(dto.response.clientDataJSON),
          attestationObject = Base64Url.decode(attestationObject),
        )
      case None                    =>
        AuthenticatorAssertionResponse(
          clientDataJSON = Base64Url.decode(dto.response.clientDataJSON),
          authenticatorData = Base64Url.decode(dto.response.authenticatorData.getOrElse("")),
          signature = Base64Url.decode(dto.response.signature.getOrElse("")),
          userHandle = dto.response.userHandle.map(Base64Url.decode),
        )
    }

    PublicKeyCredential(
      id = dto.id,
      rawId = Base64Url.decode(dto.rawId),
      response = response,
      authenticatorAttachment = dto.authenticatorAttachment.flatMap(AuthenticatorAttachment.fromString),
    )
  }
}

object WebAuthnService {
  val live: ULayer[WebAuthnService] = ZLayer {
    for {
      sessions <- Ref.make(Map.empty[String, (String, Long)])
      registeredUsers <- Ref.make(Map.empty[String, String])
      storage <- InMemoryCredentialStorage.make.orDie
      webAuthnServer = new WebAuthnServer(
        rpId = "localhost",
        rpName = "WebAuthn Demo",
        rpOrigin = "http://localhost:8080",
        storage
      )
    } yield WebAuthnServiceLive(webAuthnServer, sessions, registeredUsers)
  }
}