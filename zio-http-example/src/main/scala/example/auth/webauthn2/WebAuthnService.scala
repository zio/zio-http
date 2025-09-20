package example.auth.webauthn2

import com.yubico.webauthn._
import com.yubico.webauthn.data._
import com.yubico.webauthn.exception.RegistrationFailedException
import example.auth.webauthn2.WebAuthnUtils._
import example.auth.webauthn2.models.JsonCodecs._
import example.auth.webauthn2.models._
import zio._
import zio.json._

import java.util.Base64
import java.util.concurrent.ConcurrentHashMap
import scala.jdk.CollectionConverters._
import scala.jdk.OptionConverters._
import scala.util.Try

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
            .build(),
        )
      case _                           =>
        // Usernameless authentication for discoverable passkeys
        relyingParty.startAssertion(
          StartAssertionOptions
            .builder()
            .userVerification(UserVerificationRequirement.REQUIRED)
            .build(),
        )
    }

    // Store the assertion request - use username as key if available, otherwise use challenge
    val storageKey = username.filter(_.nonEmpty).getOrElse {
      // For usernameless flow, use challenge as key
      assertionRequestResult.getPublicKeyCredentialRequestOptions.getChallenge.getBase64Url
    }
    authenticationRequests.put(storageKey, assertionRequestResult)

    assertionRequestResult
  }

  def finishAuthentication(request: AuthenticationFinishRequest): Task[AuthenticationFinishResponse] = ZIO.attempt {
    // For discoverable passkeys, we need to identify the user from userHandle
    val actualUsername = request.username match {
      case Some(user) if user.nonEmpty => user
      case _                           =>
        // Extract username from userHandle in the response
        request.publicKeyCredential.getResponse.getUserHandle.toScala match {
          case Some(userHandle) =>
            credentialRepository
              .getUsernameForUserHandle(userHandle)
              .toScala
              .getOrElse(throw new Exception("User not found for provided handle"))
          case None             =>
            throw new Exception("No username or userHandle provided")
        }
    }

    // Find the assertion request
    val assertionRequest = request.username.filter(_.nonEmpty) match {
      case Some(user) =>
        // Username-based flow - look up by username
        Option(authenticationRequests.get(user))
          .getOrElse(throw new Exception(s"No authentication request found for user: $user"))
      case None       =>
        // Usernameless flow - need to find by matching challenge
        val clientDataJSON = request.publicKeyCredential.getResponse.getClientDataJSON.getBase64Url
        findAssertionRequestByClientData(clientDataJSON)
          .getOrElse(throw new Exception("No matching authentication request found"))
    }

    // TODO: is it important?
    credentialRepository
      .getStoredCredential(actualUsername)
      .getOrElse(throw new Exception(s"User not registered: $actualUsername"))

    // Verify the assertion
    val result =
      relyingParty.finishAssertion(
        FinishAssertionOptions
          .builder()
          .request(assertionRequest)
          .response(request.publicKeyCredential)
          .build(),
      )

    if (result.isSuccess) {
      // Update signature count
      credentialRepository.updateSignatureCount(actualUsername, result.getSignatureCount)

      // Clean up authentication request
      request.username.filter(_.nonEmpty).foreach(authenticationRequests.remove)
    }

    AuthenticationFinishResponse(
      success = result.isSuccess,
      username = actualUsername,
    )
  }

  private def findAssertionRequestByClientData(clientDataJSON: String): Option[AssertionRequest] = {
    // First decode the base64url-encoded clientDataJSON to get the actual JSON string
    val decodedJsonOpt = Try {
      // Decode base64url to get the JSON string
      val decoder      = Base64.getUrlDecoder
      val decodedBytes = decoder.decode(clientDataJSON)
      new String(decodedBytes, "UTF-8")
    }.toOption

    decodedJsonOpt.flatMap { decodedJson =>
      // Now parse the decoded JSON to extract the challenge
      val challengeOpt = decodedJson.fromJson[ClientData].toOption.map(_.challenge)

      challengeOpt.flatMap { challenge =>
        // Look for an assertion request with this challenge
        authenticationRequests.asScala.find { case (key, request) =>
          val requestChallenge = base64UrlEncode(request.getPublicKeyCredentialRequestOptions.getChallenge.getBytes)
          requestChallenge == challenge || key == challenge
        }.map(_._2)
      }
    }
  }

}
