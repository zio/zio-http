package example.auth.webauthn2

import com.yubico.webauthn._
import com.yubico.webauthn.data._
import example.auth.webauthn2.WebAuthnUtils._
import example.auth.webauthn2.models._
import zio._

import java.util.UUID
import scala.jdk.CollectionConverters._

/**
 * WebAuthn Service implementation - Discoverable passkey authentication
 * Supports both username-based and usernameless authentication flows
 */
class WebAuthnService(
  userService: UserService,
  registrationRequests: Ref[Map[UserHandle, RegistrationStartResponse]],
  authenticationRequests: Ref[Map[Challenge, AssertionRequest]],
) extends WebAuthenticationService {
  private val credentialRepository = new InMemoryCredentialRepository(userService)

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
      .credentialRepository(credentialRepository)
      .origins(Set("http://localhost:8080").asJava)
      .build()

  override def startRegistration(username: String): Task[RegistrationStartResponse] =
    for {
      user <- userService
        .getUser(username)
        .orElse {
          val user = User(UUID.randomUUID().toString, username, Set.empty)
          userService.addUser(user).as(user)
        }
        .orDieWith(_ => new Exception("User service error"))
      creationOptions = createCreationOptions(relyingPartyIdentity, username, user.userHandle)
      -    <- registrationRequests.update(_.updated(user.userHandle, creationOptions))
    } yield creationOptions

  override def finishRegistration(
    request: RegistrationFinishRequest,
  ): Task[RegistrationFinishResponse] =
    for {
      creationOptions <- registrationRequests.get
        .map(_.get(request.userhandle))
        .some
        .orElseFail(NoRegistrationRequest(request.username))
      result = relyingParty.finishRegistration(
        FinishRegistrationOptions
          .builder()
          .request(creationOptions)
          .response(request.publicKeyCredential)
          .build(),
      )
      response <-
        if (result.isUserVerified) {
          userService
            .addCredential(
              userHandle = request.userhandle,
              credential = UserCredential(
                credentialId = result.getKeyId.getId,
                publicKeyCose = result.getPublicKeyCose,
                signatureCount = result.getSignatureCount,
                userHandle = creationOptions.getUser.getId,
              ),
            )
            .orDieWith(_ => new Exception("User service error")) *>
            registrationRequests.update(_.removed(request.userhandle)) *>
            ZIO.succeed {
              RegistrationFinishResponse(
                success = true,
                credentialId = result.getKeyId.getId.getBase64Url,
              )
            }
        } else {
          ZIO.fail(UserVerificationFailed(request.username))
        }
    } yield response

  override def startAuthentication(username: Option[String]): Task[AuthenticationStartResponse] =
    for {
      assertion <- createAuthStartAssertion(relyingParty, username)
      _         <- authenticationRequests
        .update(_.updated(assertion.getPublicKeyCredentialRequestOptions.getChallenge.getBase64Url, assertion))
    } yield assertion

  override def finishAuthentication(request: AuthenticationFinishRequest): Task[AuthenticationFinishResponse] =
    for {
      assertionRequest <- authenticationRequests.get
        .map(_.get(request.publicKeyCredential.getResponse.getClientData.getChallenge.getBase64Url))
        .some
        .orElseFail(
          NoAuthenticationRequest(request.publicKeyCredential.getResponse.getClientData.getChallenge.getBase64Url),
        )
      assertion =
        relyingParty.finishAssertion(
          FinishAssertionOptions
            .builder()
            .request(assertionRequest)
            .response(request.publicKeyCredential)
            .build(),
        )
      _ <- ZIO.when(assertion.isUserVerified) {
        authenticationRequests.get.map(
          _.removed(request.publicKeyCredential.getResponse.getClientData.getChallenge.getBase64Url),
        )
      }
    } yield AuthenticationFinishResponse(
      success = assertion.isUserVerified,
      username = assertion.getUsername,
    )

  private def createAuthStartAssertion(
    relyingParty: RelyingParty,
    username: Option[String],
    timeout: Duration = 1.minutes,
  ): Task[AuthenticationStartResponse] = ZIO.attempt {
    // Create assertion request - usernameless if no username provided
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

  private def createCreationOptions(
    relyingPartyIdentity: RelyingPartyIdentity,
    username: String,
    userHandle: String,
    timeout: Duration = 1.minutes,
  ): PublicKeyCredentialCreationOptions = {
    val userIdentity: UserIdentity =
      UserIdentity
        .builder()
        .name(username)
        .displayName(username)
        .id(new ByteArray(userHandle.getBytes())) // Use unique handle instead of username bytes
        .build()

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
}
