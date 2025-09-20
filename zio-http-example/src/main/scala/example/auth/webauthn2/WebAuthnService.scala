package example.auth.webauthn2

import com.yubico.webauthn._
import com.yubico.webauthn.data._
import com.yubico.webauthn.exception.RegistrationFailedException
import example.auth.webauthn2.WebAuthnUtils._
import example.auth.webauthn2.models._
import zio._

import java.util.concurrent.ConcurrentHashMap
import scala.jdk.CollectionConverters._

/**
 * WebAuthn Service implementation - Discoverable passkey authentication
 * Supports both username-based and usernameless authentication flows
 */
class WebAuthnService {
  private val credentialRepository   = new InMemoryCredentialRepository()
  private val registrationRequests   = new ConcurrentHashMap[String, RegistrationStartResponse]()
  private val authenticationRequests = new ConcurrentHashMap[String, AssertionRequest]()

  val relyingPartyIdentity: RelyingPartyIdentity =
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

  def startRegistration(username: String): Task[RegistrationStartResponse] = ZIO.attempt {
    // Generate a unique user handle for discoverable passkeys
    val userHandle = new ByteArray(generateUserHandle(username))

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

    registrationRequests.put(username, creationOptions)

    creationOptions
  }

  def finishRegistration(
    request: RegistrationFinishRequest,
  ): Task[RegistrationFinishResponse] = ZIO.attempt {
    val creationOptions = registrationRequests.get(request.username)

    if (creationOptions == null) {
      println("No registration request found for user")
      throw new Exception("No registration request found for user")
    }

    // Verify the registration
    val result = relyingParty.finishRegistration(
      FinishRegistrationOptions
        .builder()
        .request(creationOptions)
        .response(request.publicKeyCredential)
        .build(),
    )

    if (result.isUserVerified) {

      credentialRepository.addCredential(
        credentialId = result.getKeyId.getId,
        publicKeyCose = result.getPublicKeyCose,
        signatureCount = result.getSignatureCount,
        username = request.username,
        userHandle = creationOptions.getUser.getId,
      )
      registrationRequests.remove(request.username)

      RegistrationFinishResponse(
        success = true,
        credentialId = result.getKeyId.getId.getBase64Url,
      )
    } else {
      throw new RegistrationFailedException(new IllegalArgumentException("User verification failed"))
    }
  }

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
      case _                           =>
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
