package example.auth.webauthn2

import com.yubico.webauthn._
import com.yubico.webauthn.data._
import com.yubico.webauthn.exception.{AssertionFailedException, RegistrationFailedException}
import zio._
import zio.http._
import zio.json._

import java.security.SecureRandom
import java.util.Optional
import java.util.concurrent.ConcurrentHashMap
import scala.jdk.CollectionConverters._
import scala.jdk.OptionConverters._
import scala.util.Try

/**
 * WebAuthn Server implementation - Discoverable passkey authentication
 * Supports both username-based and usernameless authentication flows
 */
object WebAuthnServer extends ZIOAppDefault {

  // JSON codecs
  implicit val byteArrayEncoder: JsonEncoder[Array[Byte]] =
    JsonEncoder.string.contramap(java.util.Base64.getUrlEncoder.withoutPadding.encodeToString(_))
  implicit val byteArrayDecoder: JsonDecoder[Array[Byte]] =
    JsonDecoder.string.map(java.util.Base64.getUrlDecoder.decode(_))

  // Request/Response DTOs
  case class RegistrationStartRequest(username: String)

  case class RegistrationStartResponse(
                                        challenge: String,
                                        rp: RpInfo,
                                        user: UserInfo,
                                        pubKeyCredParams: List[CredParam],
                                        authenticatorSelection: AuthSelection,
                                        attestation: String,
                                        timeout: Long,
                                      )

  case class RpInfo(id: String, name: String)
  case class UserInfo(id: String, name: String, displayName: String)
  case class CredParam(`type`: String, alg: Int)
  case class AuthSelection(
                            authenticatorAttachment: Option[String],
                            requireResidentKey: Boolean,
                            residentKey: String,
                            userVerification: String,
                          )

  case class RegistrationFinishRequest(
                                        username: String,
                                        id: String,
                                        rawId: String,
                                        response: AttestationResponse,
                                        `type`: String,
                                      )

  case class AttestationResponse(
                                  clientDataJSON: String,
                                  attestationObject: String,
                                )

  // Modified to make username optional for discoverable passkeys
  case class AuthenticationStartRequest(username: Option[String])

  case class AuthenticationStartResponse(
                                          challenge: String,
                                          rpId: String,
                                          allowCredentials: List[AllowedCredential],
                                          userVerification: String,
                                          timeout: Long,
                                        )

  case class AllowedCredential(`type`: String, id: String)

  // Modified to handle userHandle for discoverable passkeys
  case class AuthenticationFinishRequest(
                                          username: Option[String],  // Optional for discoverable passkeys
                                          id: String,
                                          rawId: String,
                                          response: AssertionResponse,
                                          `type`: String,
                                        )

  case class AssertionResponse(
                                clientDataJSON: String,
                                authenticatorData: String,
                                signature: String,
                                userHandle: Option[String],
                              )

  // JSON codecs
  implicit val registrationStartRequestDecoder: JsonDecoder[RegistrationStartRequest]   = DeriveJsonDecoder.gen
  implicit val rpInfoEncoder: JsonEncoder[RpInfo]                                       = DeriveJsonEncoder.gen
  implicit val userInfoEncoder: JsonEncoder[UserInfo]                                   = DeriveJsonEncoder.gen
  implicit val credParamEncoder: JsonEncoder[CredParam]                                 = DeriveJsonEncoder.gen
  implicit val authSelectionEncoder: JsonEncoder[AuthSelection]                         = DeriveJsonEncoder.gen
  implicit val registrationStartResponseEncoder: JsonEncoder[RegistrationStartResponse] = DeriveJsonEncoder.gen

  implicit val attestationResponseDecoder: JsonDecoder[AttestationResponse]             = DeriveJsonDecoder.gen
  implicit val registrationFinishRequestDecoder: JsonDecoder[RegistrationFinishRequest] = DeriveJsonDecoder.gen

  implicit val authenticationStartRequestDecoder: JsonDecoder[AuthenticationStartRequest]   = DeriveJsonDecoder.gen
  implicit val allowedCredentialEncoder: JsonEncoder[AllowedCredential]                     = DeriveJsonEncoder.gen
  implicit val authenticationStartResponseEncoder: JsonEncoder[AuthenticationStartResponse] = DeriveJsonEncoder.gen

  implicit val assertionResponseDecoder: JsonDecoder[AssertionResponse]                     = DeriveJsonDecoder.gen
  implicit val authenticationFinishRequestDecoder: JsonDecoder[AuthenticationFinishRequest] = DeriveJsonDecoder.gen

  // Storage
  case class StoredCredential(
                               credentialId: ByteArray,
                               publicKeyCose: ByteArray,
                               signatureCount: Long,
                               username: String,
                               userHandle: ByteArray,  // Added to support discoverable passkeys
                             )

  // Client data structure for parsing authentication responses
  case class ClientData(challenge: String, origin: String, `type`: String)
  implicit val clientDataDecoder: JsonDecoder[ClientData] = DeriveJsonDecoder.gen

  class WebAuthnService {
    private val users                  = new ConcurrentHashMap[String, StoredCredential]()
    private val credentialsByHandle    = new ConcurrentHashMap[ByteArray, StoredCredential]()  // Added for userHandle lookup
    private val registrationRequests   = new ConcurrentHashMap[String, PublicKeyCredentialCreationOptions]()
    private val authenticationRequests = new ConcurrentHashMap[String, AssertionRequest]()  // Using generic key for both flows

    private val rpIdentity = RelyingPartyIdentity
      .builder()
      .id("localhost")
      .name("WebAuthn Demo - Discoverable Passkeys")
      .build()

    private val rp = RelyingParty
      .builder()
      .identity(rpIdentity)
      .credentialRepository(new CredentialRepository {
        def getCredentialIdsForUsername(username: String): java.util.Set[PublicKeyCredentialDescriptor] =
          Option(users.get(username)) match {
            case Some(cred) =>
              Set(
                PublicKeyCredentialDescriptor
                  .builder()
                  .id(cred.credentialId)
                  .build(),
              ).asJava
            case None       => Set.empty[PublicKeyCredentialDescriptor].asJava
          }

        def getUserHandleForUsername(username: String): Optional[ByteArray] =
          Option(users.get(username)) match {
            case Some(cred) => Optional.of(cred.userHandle)
            case None       => Optional.empty()
          }

        def getUsernameForUserHandle(userHandle: ByteArray): Optional[String] =
          Option(credentialsByHandle.get(userHandle)) match {
            case Some(cred) => Optional.of(cred.username)
            case None       => Optional.empty()
          }

        def lookup(credentialId: ByteArray, userHandle: ByteArray): Optional[RegisteredCredential] =
          Option(credentialsByHandle.get(userHandle)) match {
            case Some(cred) if cred.credentialId.equals(credentialId) =>
              Optional.of(
                RegisteredCredential
                  .builder()
                  .credentialId(cred.credentialId)
                  .userHandle(userHandle)
                  .publicKeyCose(cred.publicKeyCose)
                  .signatureCount(cred.signatureCount)
                  .build(),
              )
            case _                                                    => Optional.empty()
          }

        def lookupAll(credentialId: ByteArray): java.util.Set[RegisteredCredential] =
          users
            .values()
            .asScala
            .find(_.credentialId.equals(credentialId))
            .map { cred =>
              Set(
                RegisteredCredential
                  .builder()
                  .credentialId(cred.credentialId)
                  .userHandle(cred.userHandle)
                  .publicKeyCose(cred.publicKeyCose)
                  .signatureCount(cred.signatureCount)
                  .build(),
              ).asJava
            }
            .getOrElse(Set.empty[RegisteredCredential].asJava)
      })
      .origins(Set("http://localhost:8080").asJava)
      .build()

    def startRegistration(username: String): Task[RegistrationStartResponse] = ZIO.attempt {
      // Generate a unique user handle for discoverable passkeys
      val userHandle = new ByteArray(generateUserHandle(username))

      val userIdentity = UserIdentity
        .builder()
        .name(username)
        .displayName(username)
        .id(userHandle)  // Use unique handle instead of username bytes
        .build()

      val creationOptions: PublicKeyCredentialCreationOptions = PublicKeyCredentialCreationOptions
        .builder()
        .rp(rpIdentity)
        .user(userIdentity)
        .challenge(generateChallenge())
        .pubKeyCredParams(
          List(
            PublicKeyCredentialParameters.ES256,
            PublicKeyCredentialParameters.RS256,
          ).asJava,
        )
        .authenticatorSelection(
          AuthenticatorSelectionCriteria
            .builder()
            .residentKey(ResidentKeyRequirement.REQUIRED)  // Changed to REQUIRED for discoverable passkeys
            .userVerification(UserVerificationRequirement.REQUIRED)  // Changed to REQUIRED for better security
            .build(),
        )
        .attestation(AttestationConveyancePreference.NONE)
        .timeout(60000L)
        .build()

      registrationRequests.put(username, creationOptions)
      creationOptions.toJson

      RegistrationStartResponse(
        challenge = base64UrlEncode(creationOptions.getChallenge.getBytes),
        rp = RpInfo(rpIdentity.getId, rpIdentity.getName),
        user = UserInfo(
          base64UrlEncode(userIdentity.getId.getBytes),
          userIdentity.getName,
          userIdentity.getDisplayName,
        ),
        pubKeyCredParams = List(
          CredParam("public-key", -7),  // ES256
          CredParam("public-key", -257), // RS256
        ),
        authenticatorSelection = AuthSelection(
          authenticatorAttachment = None,
          requireResidentKey = true,  // Changed to true
          residentKey = "required",    // Changed to "required"
          userVerification = "required", // Changed to "required"
        ),
        attestation = "none",
        timeout = 60000L,
      )
    }

    def finishRegistration(request: RegistrationFinishRequest): Task[String] = ZIO.attempt {
      val creationOptions = registrationRequests.get(request.username)
      if (creationOptions == null) throw new Exception("No registration request found for user")

      // Create the credential from the client response
      val credential = PublicKeyCredential
        .builder()
        .id(new ByteArray(base64UrlDecode(request.rawId)))
        .response(
          AuthenticatorAttestationResponse
            .builder()
            .attestationObject(new ByteArray(base64UrlDecode(request.response.attestationObject)))
            .clientDataJSON(new ByteArray(base64UrlDecode(request.response.clientDataJSON)))
            .build(),
        )
        .clientExtensionResults(ClientRegistrationExtensionOutputs.builder().build())
        .build()

      // Verify the registration
      val result = rp.finishRegistration(
        FinishRegistrationOptions
          .builder()
          .request(creationOptions)
          .response(credential)
          .build(),
      )

      if (result.isUserVerified) {
        // Get the user handle from the creation options
        val userHandle = creationOptions.getUser.getId

        // Store the credential with user handle
        val storedCredential = StoredCredential(
          credentialId = result.getKeyId.getId,
          publicKeyCose = result.getPublicKeyCose,
          signatureCount = result.getSignatureCount,
          username = request.username,
          userHandle = userHandle,
        )

        users.put(request.username, storedCredential)
        credentialsByHandle.put(userHandle, storedCredential)
        registrationRequests.remove(request.username)

        "Registration successful - Discoverable passkey created!"
      } else {
        throw new RegistrationFailedException(new IllegalArgumentException("User verification failed"))
      }
    }

    def startAuthentication(username: Option[String]): Task[AuthenticationStartResponse] = ZIO.attempt {
      // Create assertion request - usernameless if no username provided
      val assertionRequestResult = username match {
        case Some(user) if user.nonEmpty =>
          // Username-based authentication
          rp.startAssertion(
            StartAssertionOptions
              .builder()
              .username(user)
              .userVerification(UserVerificationRequirement.REQUIRED)
              .build(),
          )
        case _ =>
          // Usernameless authentication for discoverable passkeys
          rp.startAssertion(
            StartAssertionOptions
              .builder()
              .userVerification(UserVerificationRequirement.REQUIRED)
              .build(),
          )
      }

      // Store the assertion request - use username as key if available, otherwise use challenge
      val storageKey = username.filter(_.nonEmpty).getOrElse {
        // For usernameless flow, use challenge as key
        base64UrlEncode(assertionRequestResult.getPublicKeyCredentialRequestOptions.getChallenge.getBytes)
      }
      authenticationRequests.put(storageKey, assertionRequestResult)

      // Get the request options from the assertion request
      val requestOptions = assertionRequestResult.getPublicKeyCredentialRequestOptions

      // Prepare response for client
      val allowCredentials = requestOptions.getAllowCredentials.toScala
        .map(_.asScala)
        .toList
        .flatten
        .map { cred =>
          AllowedCredential(
            "public-key",
            base64UrlEncode(cred.getId.getBytes),
          )
        }

      AuthenticationStartResponse(
        challenge = base64UrlEncode(requestOptions.getChallenge.getBytes),
        rpId = requestOptions.getRpId,
        allowCredentials = allowCredentials,  // Empty for usernameless flow
        userVerification = requestOptions.getUserVerification.toScala.getOrElse(UserVerificationRequirement.REQUIRED).toString.toLowerCase,
        timeout = requestOptions.getTimeout.toScala.getOrElse(60000L).asInstanceOf[Long],
      )
    }

    def finishAuthentication(request: AuthenticationFinishRequest): Task[String] = ZIO.attempt {
      // For discoverable passkeys, we need to identify the user from userHandle
      val actualUsername = request.username match {
        case Some(user) if user.nonEmpty => user
        case _ =>
          // Extract username from userHandle in the response
          request.response.userHandle match {
            case Some(handleStr) =>
              val userHandle = new ByteArray(base64UrlDecode(handleStr))
              Option(credentialsByHandle.get(userHandle))
                .map(_.username)
                .getOrElse(throw new Exception("User not found for provided handle"))
            case None =>
              throw new Exception("No username or userHandle provided")
          }
      }

      // Find the assertion request
      // First try with the username (for username-based flow)
      // Then try with challenge (for usernameless flow)
      val assertionRequest = request.username.filter(_.nonEmpty) match {
        case Some(user) =>
          // Username-based flow - look up by username
          Option(authenticationRequests.get(user))
            .getOrElse(throw new Exception(s"No authentication request found for user: $user"))
        case None =>
          // Usernameless flow - need to find by matching challenge
          // Extract challenge from clientDataJSON to find the matching request
          val clientDataJSON = new String(base64UrlDecode(request.response.clientDataJSON))
          findAssertionRequestByClientData(clientDataJSON)
            .getOrElse(throw new Exception("No matching authentication request found"))
      }

      val storedCred = users.get(actualUsername)
      if (storedCred == null) throw new Exception(s"User not registered: $actualUsername")

      // Create the credential from the client response
      val credential = PublicKeyCredential
        .builder()
        .id(new ByteArray(base64UrlDecode(request.rawId)))
        .response(
          AuthenticatorAssertionResponse
            .builder()
            .authenticatorData(new ByteArray(base64UrlDecode(request.response.authenticatorData)))
            .clientDataJSON(new ByteArray(base64UrlDecode(request.response.clientDataJSON)))
            .signature(new ByteArray(base64UrlDecode(request.response.signature)))
            .userHandle(request.response.userHandle.map(uh => new ByteArray(base64UrlDecode(uh))).toJava)
            .build(),
        )
        .clientExtensionResults(ClientAssertionExtensionOutputs.builder().build())
        .build()

      // Verify the assertion
      val result = try {
        rp.finishAssertion(
          FinishAssertionOptions
            .builder()
            .request(assertionRequest)
            .response(credential)
            .build(),
        )
      } catch {
        case e: Exception =>
          println(s"Assertion verification failed: ${e.getMessage}")
          throw new AssertionFailedException(s"Authentication verification failed: ${e.getMessage}")
      }

      if (result.isSuccess) {
        // Update signature count
        val updatedCred = storedCred.copy(signatureCount = result.getSignatureCount)
        users.put(actualUsername, updatedCred)
        credentialsByHandle.put(updatedCred.userHandle, updatedCred)

        // Clean up authentication request
        request.username.filter(_.nonEmpty).foreach(authenticationRequests.remove)
        cleanupAuthenticationRequests()

        s"Authentication successful for user: $actualUsername"
      } else {
        println(s"Authentication result not successful for user: $actualUsername")
        throw new AssertionFailedException("Authentication verification failed")
      }
    }

    private def generateChallenge(): ByteArray = {
      val bytes = new Array[Byte](32)
      new SecureRandom().nextBytes(bytes)
      new ByteArray(bytes)
    }

    private def generateUserHandle(username: String): Array[Byte] = {
      // Generate a unique handle combining username and random bytes
      val random = new Array[Byte](16)
      new SecureRandom().nextBytes(random)
      val combined = username.getBytes() ++ random
      java.security.MessageDigest.getInstance("SHA-256").digest(combined).take(32)
    }

    private def findAssertionRequestByClientData(clientDataJSON: String): Option[AssertionRequest] = {
      // Parse the clientDataJSON to extract the challenge
      val challengeOpt = Try(clientDataJSON.fromJson[ClientData].toOption.map(_.challenge)).toOption.flatten

      challengeOpt.flatMap { challenge =>
        // Look for an assertion request with this challenge
        authenticationRequests.asScala.find { case (key, request) =>
          val requestChallenge = base64UrlEncode(request.getPublicKeyCredentialRequestOptions.getChallenge.getBytes)
          requestChallenge == challenge || key == challenge
        }.map(_._2)
      }
    }

    private def cleanupAuthenticationRequests(): Unit = {
      // Clean up old requests (simplified - in production, use proper cleanup with timestamps)
      if (authenticationRequests.size() > 100) {
        authenticationRequests.clear()
      }
    }

    private def base64UrlEncode(bytes: Array[Byte]): String =
      java.util.Base64.getUrlEncoder.withoutPadding.encodeToString(bytes)

    private def base64UrlDecode(str: String): Array[Byte] =
      java.util.Base64.getUrlDecoder.decode(str)
  }

  def routes(service: WebAuthnService): Routes[Any, Response] =
    Routes(
      // Serve the HTML client
      Method.GET / Root -> Handler
        .fromResource("webauthn2-client.html")
        .orElse(Handler.internalServerError("Failed to load HTML")),

      // Registration endpoints
      Method.POST / "api" / "webauthn" / "registration" / "start"  -> handler { (req: Request) =>
        for {
          body     <- req.body.asString
          request  <- ZIO.fromEither(body.fromJson[RegistrationStartRequest]).mapError(_.toString)
          response <- service.startRegistration(request.username).mapError(_.getMessage)
        } yield Response.json(response.toJson)
      },
      Method.POST / "api" / "webauthn" / "registration" / "finish" -> handler { (req: Request) =>
        for {
          body    <- req.body.asString
          request <- ZIO.fromEither(body.fromJson[RegistrationFinishRequest]).mapError(_.toString)
          result  <- service.finishRegistration(request).mapError(_.getMessage)
        } yield Response.text(result)
      },

      // Authentication endpoints
      Method.POST / "api" / "webauthn" / "authentication" / "start"  -> handler { (req: Request) =>
        for {
          body     <- req.body.asString
          request  <- ZIO.fromEither(body.fromJson[AuthenticationStartRequest]).mapError(_.toString)
          response <- service.startAuthentication(request.username).mapError(_.getMessage)
        } yield Response.json(response.toJson)
      },
      Method.POST / "api" / "webauthn" / "authentication" / "finish" -> handler { (req: Request) =>
        for {
          body    <- req.body.asString
          request <- ZIO.fromEither(body.fromJson[AuthenticationFinishRequest]).mapError(_.toString)
          result  <- service.finishAuthentication(request).mapError(_.getMessage)
        } yield Response.text(result)
      },
    ).sandbox @@ Middleware.cors

  override val run =
    ZIO
      .succeed(new WebAuthnService())
      .flatMap(service =>
        Console
          .printLine("WebAuthn Discoverable Passkeys server started on http://localhost:8080")
          .flatMap(_ =>
            Console
              .printLine("Open your browser and navigate to http://localhost:8080")
              .flatMap(_ => Server.serve(routes(service)).provide(Server.default)),
          ),
      )
}
