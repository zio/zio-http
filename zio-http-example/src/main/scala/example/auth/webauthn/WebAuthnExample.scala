package example.auth.webauthn

import example.auth.webauthn.Types._
import zio._

import java.security.MessageDigest

object WebAuthnExample extends ZIOAppDefault {

  def createExampleRegistrationOptions(): PublicKeyCredentialCreationOptions = {
    val challenge = scala.util.Random.nextBytes(32)

    PublicKeyCredentialCreationOptions(
      rp = PublicKeyCredentialRpEntity(
        name = "Example Corp",
        id = Some("example.com"),
      ),
      user = PublicKeyCredentialUserEntity(
        name = "user@example.com",
        id = scala.util.Random.nextBytes(32),
        displayName = "Example User",
      ),
      challenge = challenge,
      pubKeyCredParams = Seq(
        PublicKeyCredentialParameters(PublicKeyCredentialType.PublicKey, -7),  // ES256
        PublicKeyCredentialParameters(PublicKeyCredentialType.PublicKey, -257), // RS256
      ),
      timeout = Some(60000),
      authenticatorSelection = Some(
        AuthenticatorSelectionCriteria(
          userVerification = UserVerificationRequirement.Preferred,
        ),
      ),
      attestation = AttestationConveyancePreference.None,
    )
  }

  def createExampleAuthenticationOptions(): PublicKeyCredentialRequestOptions = {
    val challenge = scala.util.Random.nextBytes(32)

    PublicKeyCredentialRequestOptions(
      challenge = challenge,
      timeout = Some(60000),
      rpId = Some("example.com"),
      userVerification = UserVerificationRequirement.Preferred,
    )
  }

  def demonstrateWebAuthnServer(): ZIO[Any, Throwable, Unit] = {
    for {
      _ <- Console.printLine("WebAuthn Server Implementation Demo")
      _ <- Console.printLine("===================================")
      storage <- InMemoryCredentialStorage.make
      server = new WebAuthnServer(
        rpId = "example.com",
        rpName = "Example Corp",
        rpOrigin = "https://example.com",
        storage
      )

      userHandle = scala.util.Random.nextBytes(32)

      // Start registration
      registrationOptions <- server.startRegistration(
        userName = "user@example.com",
        userDisplayName = "Example User",
        userHandle = userHandle,
      )

      _ <- Console.printLine(s"Registration started for RP: ${registrationOptions.rp.name}")
      _ <- Console.printLine(s"Challenge: ${Base64Url.encode(registrationOptions.challenge)}")

      // Start authentication
      result <- server.startAuthentication()
      (sessionId, authOptions) = result
      _ <- Console.printLine(s"Authentication session: $sessionId")
      _ <- Console.printLine(s"Auth challenge: ${Base64Url.encode(authOptions.challenge)}")

    } yield ()
  }

  def demonstrateVerification(): ZIO[Any, Throwable, Unit] = {
    for {
      _ <- Console.printLine("WebAuthn Scala Implementation Demo")
      _ <- Console.printLine("==================================")

      // Create example options
      registrationOptions   = createExampleRegistrationOptions()
      authenticationOptions = createExampleAuthenticationOptions()

      _ <- Console.printLine(s"Registration options created for RP: ${registrationOptions.rp.name}")
      _ <- Console.printLine(s"Challenge length: ${registrationOptions.challenge.length} bytes")
      _ <- Console.printLine(s"Supported algorithms: ${registrationOptions.pubKeyCredParams.map(_.alg).mkString(", ")}")

      // Example authenticator data
      exampleAuthData = AuthenticatorData(
        rpIdHash = MessageDigest.getInstance("SHA-256").digest("example.com".getBytes("UTF-8")),
        flags = AuthenticatorDataFlags(userPresent = true, userVerified = true),
        signCount = 1,
        attestedCredentialData = Some(
          AttestedCredentialData(
            aaguid = new Array[Byte](16),
            credentialIdLength = 32,
            credentialId = scala.util.Random.nextBytes(32),
            credentialPublicKey = scala.util.Random.nextBytes(77), // Example COSE key
          ),
        ),
      )

      _ <- Console.printLine(s"Example authenticator data size: ${exampleAuthData.toBytes.length} bytes")
      _ <- Console.printLine(s"User present: ${exampleAuthData.flags.userPresent}")
      _ <- Console.printLine(s"User verified: ${exampleAuthData.flags.userVerified}")

      // Demonstrate server
      _ <- demonstrateWebAuthnServer()

      // Demonstrate extensions
      _ <- Console.printLine("\nSupported Extensions:")
      _ <- ZIO.foreachDiscard(Extensions.getSupportedExtensions) { case (id, ext) =>
        Console.printLine(s"- $id: ${ext.getClass.getSimpleName}")
      }

    } yield ()
  }

  def run: ZIO[Any, Throwable, Unit] = {
    demonstrateVerification()
  }
}
