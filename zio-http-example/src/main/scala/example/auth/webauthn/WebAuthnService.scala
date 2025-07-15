package example.auth.webauthn

import example.auth.webauthn.Types.Base64Url
import zio._
import scala.util.Random

import scala.util.Random.javaRandomToRandom
// ============================================================================
// WebAuthn Service
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
                              ) extends WebAuthnService {

  def startRegistration(request: StartRegistrationRequest): IO[String, StartRegistrationResponse] =
    for {
      _ <- ZIO.debug("Starting registration process... 00000000000000000000000000000000000000000000000000")
      userHandle <- ZIO.succeed(generateUserHandle())
      sessionId = java.util.UUID.randomUUID().toString

      _ <- ZIO.debug("-----------------------" + request)
      // Debug logging
      _ <- ZIO.logInfo(s"Starting registration for user: ${request.username}, sessionId: $sessionId")

      _ <- sessions.update(_ + (sessionId -> (request.username, java.lang.System.currentTimeMillis())))

      // Debug: check if session was stored
      storedSessions <- sessions.get
      _ <- ZIO.logInfo(s"Stored sessions after registration start: ${storedSessions.keys}")

      // Convert request parameters
      authenticatorSelection = createAuthenticatorSelection(request)
      _ <- ZIO.debug("*************" + authenticatorSelection)
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
    for {
      // Debug logging
      _ <- ZIO.logInfo(s"Finishing registration with sessionId: ${request.sessionId}")

      currentSessions <- sessions.get
      _ <- ZIO.logInfo(s"Current sessions: ${currentSessions.keys}")

      sessionData <- ZIO.fromOption(currentSessions.get(request.sessionId))
        .orElseFail(s"Invalid session ID: ${request.sessionId}. Available sessions: ${currentSessions.keys}")

      (username, timestamp) = sessionData
      _ <- ZIO.logInfo(s"Found session for user: $username, timestamp: $timestamp")

      currentTime = java.lang.System.currentTimeMillis()
      _ <- ZIO.logInfo(s"Current time: $currentTime, session age: ${currentTime - timestamp}ms")

      _ <- ZIO
        .fail("Session expired")
        .when(currentTime - timestamp > 300000) // 5 minutes

      // Convert DTO to domain objects
      credential = convertFromCredentialDTO(request.credential)
      userHandle = generateUserHandle() // In real app, retrieve from session

      // For debugging, let's try to finish registration without the WebAuthn server first
      _ <- ZIO.logInfo("Attempting to finish registration...")

      // Simplified response for debugging
      _ <- sessions.update(_ - request.sessionId)
      _ <- ZIO.logInfo("Session cleaned up successfully")

      response = FinishRegistrationResponse(
        success = true,
        credentialId = "debug-credential-id",
        message = "Registration successful (debug mode)",
      )

      _ <- ZIO.logInfo(s"Registration finished successfully for user: $username")
    } yield response

  def startAuthentication(request: StartAuthenticationRequest): IO[String, StartAuthenticationResponse] =
    for {
      _ <- ZIO.unit
      sessionId = java.util.UUID.randomUUID().toString
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
    for {
      // Debug logging
      _ <- ZIO.logInfo(s"Finishing authentication with sessionId: ${request.sessionId}")

      currentSessions <- sessions.get
      _ <- ZIO.logInfo(s"Current sessions: ${currentSessions.keys}")

      sessionData <- ZIO.fromOption(currentSessions.get(request.sessionId))
        .orElseFail(s"Invalid session ID: ${request.sessionId}. Available sessions: ${currentSessions.keys}")

      (username, timestamp) = sessionData
      _ <- ZIO.logInfo(s"Found session for user: $username, timestamp: $timestamp")

      currentTime = java.lang.System.currentTimeMillis()
      _ <- ZIO.logInfo(s"Current time: $currentTime, session age: ${currentTime - timestamp}ms")

      _ <- ZIO
        .fail("Session expired")
        .when(currentTime - timestamp > 300000) // 5 minutes

      // Convert DTO to domain objects
      credential = convertFromCredentialDTO(request.credential)

      // For debugging, let's try to finish authentication without the WebAuthn server first
      _ <- ZIO.logInfo("Attempting to finish authentication...")

      // Simplified response for debugging
      _ <- sessions.update(_ - request.sessionId)
      _ <- ZIO.logInfo("Session cleaned up successfully")

      response = FinishAuthenticationResponse(
        success = true,
        username = Some(username),
        message = "Authentication successful (debug mode)",
      )

      _ <- ZIO.logInfo(s"Authentication finished successfully for user: $username")
    } yield response

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
      webAuthnServer = new WebAuthnServer(
        rpId = "localhost",
        rpName = "WebAuthn Demo",
        rpOrigin = "http://localhost:8080",
      )
    } yield WebAuthnServiceLive(webAuthnServer, sessions)
  }
}

