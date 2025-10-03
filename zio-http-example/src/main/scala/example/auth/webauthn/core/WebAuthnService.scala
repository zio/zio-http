package example.auth.webauthn.core

import com.yubico.webauthn._
import com.yubico.webauthn.data._
import example.auth.webauthn.model._
import zio._

import java.security.SecureRandom
import java.util.UUID
import scala.jdk.CollectionConverters._

trait WebAuthnService {
  def startRegistration(request: RegistrationStartRequest): IO[String, RegistrationStartResponse]
  def finishRegistration(request: RegistrationFinishRequest): IO[String, RegistrationFinishResponse]
  def startAuthentication(request: AuthenticationStartRequest): IO[String, AuthenticationStartResponse]
  def finishAuthentication(request: AuthenticationFinishRequest): IO[String, AuthenticationFinishResponse]
}

/**
 * WebAuthn Service implementation - Discoverable passkey authentication
 * Supports both username-based and usernameless authentication flows
 */
class WebAuthnServiceImpl(
  userService: UserService,
  pendingRegistrations: Ref[Map[UserHandle, RegistrationStartResponse]],
  pendingAuthentications: Ref[Map[Challenge, AuthenticationStartResponse]],
) extends WebAuthnService {
  private val relyingPartyIdentity: RelyingPartyIdentity =
    RelyingPartyIdentity
      .builder()
      .id("localhost")
      .name("WebAuthn Demo")
      .build()

  private val relyingParty: RelyingParty =
    RelyingParty
      .builder()
      .identity(relyingPartyIdentity)
      .credentialRepository(new InMemoryCredentialRepository(userService))
      .origins(Set("http://localhost:8080").asJava)
      .build()

  private def userIdentity(userId: String, username: String): UserIdentity =
    UserIdentity
      .builder()
      .name(username)
      .displayName(username)
      .id(new ByteArray(userId.getBytes())) // Use unique handle instead of username bytes
      .build()

  override def startRegistration(request: RegistrationStartRequest): ZIO[Any, Nothing, RegistrationStartResponse] =
    userService
      .getUser(request.username)
      .orElse {
        val user = User(UUID.randomUUID().toString, request.username, Set.empty)
        userService.addUser(user).as(user)
      }
      .orDieWith(_ => new IllegalStateException("Unexpected status in registration flow!"))
      .flatMap { user =>
        val creationOptions =
          generateCreationOptions(relyingPartyIdentity, userIdentity(user.userHandle, request.username));
        pendingRegistrations.update(_.updated(user.userHandle, creationOptions)).as(creationOptions)
      }

  override def finishRegistration(
    request: RegistrationFinishRequest,
  ): IO[String, RegistrationFinishResponse] =
    for {
      creationOptions <- pendingRegistrations.get
        .map(_.get(request.userhandle))
        .some
        .orElseFail(s"no registration request found for ${request.username} username")
      result = relyingParty.finishRegistration(
        FinishRegistrationOptions
          .builder()
          .request(creationOptions)
          .response(request.publicKeyCredential)
          .build(),
      )
      _ <- userService
        .addCredential(
          userHandle = request.userhandle,
          credential = UserCredential(
            userHandle = creationOptions.getUser.getId,
            credentialId = result.getKeyId.getId,
            publicKeyCose = result.getPublicKeyCose,
            signatureCount = result.getSignatureCount,
          ),
        )
        .orElseFail(s"${request.username} user not found!")
      _ <- pendingRegistrations.update(_.removed(request.userhandle))
    } yield {
      RegistrationFinishResponse(
        success = true,
        credentialId = result.getKeyId.getId.getBase64Url,
      )
    }

  override def startAuthentication(
    request: AuthenticationStartRequest,
  ): ZIO[Any, Nothing, AuthenticationStartResponse] = {
    val assertion = generateAssertionRequest(relyingParty, request.username)
    val challenge = assertion.getPublicKeyCredentialRequestOptions.getChallenge.getBase64Url; (assertion, challenge)
    pendingAuthentications.update(_.updated(challenge, assertion)).as(assertion)
  }

  override def finishAuthentication(
    request: AuthenticationFinishRequest,
  ): IO[String, AuthenticationFinishResponse] =
    for {
      challenge        <- ZIO
        .succeed(request.publicKeyCredential.getResponse.getClientData.getChallenge.getBase64Url)
      assertionRequest <- pendingAuthentications.get
        .map(_.get(challenge))
        .some
        .orElseFail(s"The ${challenge} not found in pending authentication requests!")
      assertion =
        relyingParty.finishAssertion(
          FinishAssertionOptions
            .builder()
            .request(assertionRequest)
            .response(request.publicKeyCredential)
            .build(),
        )
      _ <- pendingAuthentications.update(_.removed(challenge))
    } yield AuthenticationFinishResponse(
      success = assertion.isSuccess,
      username = assertion.getUsername,
    )

  private def generateAssertionRequest(
    relyingParty: RelyingParty,
    username: Option[String],
    timeout: Duration = 1.minutes,
  ): AssertionRequest = {
    // Create assertion request
    username match {
      case Some(user) if user.nonEmpty =>
        // Username-based authentication
        relyingParty.startAssertion(
          StartAssertionOptions
            .builder()
            .username(user)
            .userVerification(UserVerificationRequirement.REQUIRED)
            .timeout(timeout.toMillis)
            .build(),
        )

      case _ =>
        // Usernameless authentication for discoverable passkeys
        relyingParty.startAssertion(
          StartAssertionOptions
            .builder()
            .userVerification(UserVerificationRequirement.REQUIRED)
            .timeout(timeout.toMillis)
            .build(),
        )
    }
  }

  private def generateCreationOptions(
    relyingPartyIdentity: RelyingPartyIdentity,
    userIdentity: UserIdentity,
    timeout: Duration = 1.minutes,
  ): PublicKeyCredentialCreationOptions = {
    PublicKeyCredentialCreationOptions
      .builder()
      .rp(relyingPartyIdentity)
      .user(userIdentity)
      .challenge(generateChallenge())
      .pubKeyCredParams(
        List(
          PublicKeyCredentialParameters.EdDSA,
          PublicKeyCredentialParameters.ES256,
          PublicKeyCredentialParameters.RS256,
        ).asJava,
      )
      .authenticatorSelection(
        AuthenticatorSelectionCriteria
          .builder()
          .residentKey(ResidentKeyRequirement.REQUIRED)
          .userVerification(UserVerificationRequirement.REQUIRED)
          .build(),
      )
      .attestation(AttestationConveyancePreference.NONE)
      .timeout(timeout.toMillis)
      .build()
  }

  private object Crypto {
    val secureRandom: SecureRandom = new SecureRandom()
  }

  private def generateChallenge(): ByteArray = {
    val bytes = new Array[Byte](32)
    Crypto.secureRandom.nextBytes(bytes)
    new ByteArray(bytes)
  }
}

object WebAuthnServiceImpl {
  def layer: ZLayer[UserService, Nothing, WebAuthnServiceImpl] =
    ZLayer {
      for {
        userService            <- ZIO.service[UserService]
        registrationRequests   <- Ref.make(Map.empty[UserHandle, RegistrationStartResponse])
        authenticationRequests <- Ref.make(Map.empty[Challenge, AuthenticationStartResponse])
      } yield new WebAuthnServiceImpl(userService, registrationRequests, authenticationRequests)
    }
}
