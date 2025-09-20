package example.auth.webauthn2

import com.yubico.webauthn._
import com.yubico.webauthn.data._
import example.auth.webauthn2.WebAuthnUtils._
import example.auth.webauthn2.models._
import zio._

import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import scala.jdk.CollectionConverters._

/**
 * WebAuthn Service implementation - Discoverable passkey authentication
 * Supports both username-based and usernameless authentication flows
 */
class WebAuthnService(userService: UserService) {
  private type UserHandle = String
  private type Challenge  = String
  private val credentialRepository   = new InMemoryCredentialRepository(userService)
  private val registrationRequests   = new ConcurrentHashMap[UserHandle, RegistrationStartResponse]()
  private val authenticationRequests = new ConcurrentHashMap[Challenge, AssertionRequest]()

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

  def startRegistration(username: String): Task[RegistrationStartResponse] = {
    userService
      .getUser(username)
      .orElse {
        val user = User(UUID.randomUUID().toString, username, Set.empty)
        userService.addUser(user).as(user)
      }
      .orDieWith(_ => new Exception("User service error"))
  }.map { user =>
    // Generate a unique user handle for discoverable passkeys
    val userHandle = new ByteArray(user.userHandle.getBytes())

    val userIdentity: UserIdentity =
      UserIdentity
        .builder()
        .name(username)
        .displayName(username)
        .id(userHandle) // Use unique handle instead of username bytes
        .build()

    val creationOptions: PublicKeyCredentialCreationOptions =
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
        .timeout(60000L)
        .build()

    registrationRequests.put(user.userHandle, creationOptions)

    creationOptions
  }

  def finishRegistration(
    request: RegistrationFinishRequest,
  ): Task[RegistrationFinishResponse] =
    for {
      creationOptions <- ZIO
        .fromOption(Option(registrationRequests.get(request.userhandle)))
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
                username = request.username,
                userHandle = creationOptions.getUser.getId,
              ),
            )
            .orDieWith(_ => new Exception("User service error")) *>
            ZIO.succeed {
              registrationRequests.remove(request.username)

              RegistrationFinishResponse(
                success = true,
                credentialId = result.getKeyId.getId.getBase64Url,
              )
            }
        } else {
          ZIO.fail(UserVerificationFailed(request.username))
        }
    } yield response

  def startAuthentication(username: Option[String]): Task[AuthenticationStartResponse] = ZIO.attempt {
    // Create assertion request - usernameless if no username provided
    val assertionRequestResult = username match {
      case Some(user) if user.nonEmpty =>
        // Username-based authentication
        relyingParty.startAssertion(
          StartAssertionOptions
            .builder()
            .username(user)
            .userVerification(UserVerificationRequirement.REQUIRED)
            .timeout(60000L)
            .build(),
        )

      case _ =>
        // Usernameless authentication for discoverable passkeys
        relyingParty.startAssertion(
          StartAssertionOptions
            .builder()
            .userVerification(UserVerificationRequirement.REQUIRED)
            .timeout(60000L)
            .build(),
        )
    }

    val storageKey = assertionRequestResult.getPublicKeyCredentialRequestOptions.getChallenge.getBase64Url
    authenticationRequests.put(storageKey, assertionRequestResult)

    assertionRequestResult
  }

  def finishAuthentication(request: AuthenticationFinishRequest): Task[AuthenticationFinishResponse] = ZIO.attempt {
    val challenge        = request.publicKeyCredential.getResponse.getClientData.getChallenge.getBase64Url
    val assertionRequest = authenticationRequests.get(challenge)

    val result =
      relyingParty.finishAssertion(
        FinishAssertionOptions
          .builder()
          .request(assertionRequest)
          .response(request.publicKeyCredential)
          .build(),
      )

    if (result.isSuccess) {
      authenticationRequests.remove(challenge)
    }

    AuthenticationFinishResponse(
      success = result.isSuccess,
      username = result.getUsername,
    )
  }
}
