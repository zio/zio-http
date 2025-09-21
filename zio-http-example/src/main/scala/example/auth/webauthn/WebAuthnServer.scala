package example.auth.webauthn

import com.yubico.webauthn.AssertionRequest
import example.auth.webauthn._
import example.auth.webauthn.models.RegistrationStartResponse
import zio._
import zio.http._

/**
 * WebAuthn Server application - Main entry point Supports discoverable passkey
 * authentication with both username-based and usernameless authentication flows
 */
object WebAuthnServer extends ZIOAppDefault {

  override val run = {
    for {
      _ <- Console.printLine("=" * 60)
      _ <- Console.printLine("WebAuthn Discoverable Passkeys Server")
      _ <- Console.printLine("=" * 60)
      _ <- Console.printLine("Server started on http://localhost:8080")
      _ <- Console.printLine("Open your browser and navigate to http://localhost:8080")
      _ <- Console.printLine("")
      _ <- Console.printLine("Features:")
      _ <- Console.printLine("- Username-based authentication")
      _ <- Console.printLine("- Usernameless authentication (discoverable passkeys)")
      _ <- Console.printLine("- Resident key support")
      _ <- Console.printLine("- User verification required")
      _ <- Console.printLine("=" * 60)
    } yield ()
  } *>
    {
      for {
        us <- UserService.make()
        rr <- Ref.make(Map.empty[String, RegistrationStartResponse])
        ar <- Ref.make(Map.empty[String, AssertionRequest])
      } yield WebAuthnRoutes(new WebAuthnServiceImpl(us, rr, ar))
    }.flatMap { routes =>
      Server.serve(routes).provide(Server.default)
    }
}
