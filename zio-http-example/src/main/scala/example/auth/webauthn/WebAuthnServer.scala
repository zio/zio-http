package example.auth.webauthn

import zio._

import zio.http._

import example.auth.webauthn.core.{UserService, WebAuthnServiceImpl}
import example.auth.webauthn.model._

/**
 * WebAuthn Server application - Main entry point Supports discoverable passkey
 * authentication with both username-based and usernameless authentication flows
 */
object WebAuthnServer extends ZIOAppDefault {

  override val run = {
    for {
      _  <- printBanner
      us <- UserService.make()
      rr <- Ref.make(Map.empty[String, RegistrationStartResponse])
      ar <- Ref.make(Map.empty[String, AuthenticationStartResponse])
    } yield WebAuthnRoutes(new WebAuthnServiceImpl(us, rr, ar))
  }.flatMap(Server.serve(_).provide(Server.default))

  private def printBanner = {
    for {
      _ <- Console.printLine("=" * 60)
      _ <- Console.printLine("Webauthn Server")
      _ <- Console.printLine("=" * 60)
      _ <- Console.printLine("Server started on http://localhost:8080")
      _ <- Console.printLine("Open your browser and navigate to http://localhost:8080")
      _ <- Console.printLine("")
      _ <- Console.printLine("Supports both:")
      _ <- Console.printLine("- Username-based authentication")
      _ <- Console.printLine("- Usernameless authentication (discoverable passkeys)")
      _ <- Console.printLine("=" * 60)
    } yield ()
  }
}
