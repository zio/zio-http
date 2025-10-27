package example.auth.webauthn

import zio._
import zio.http._

import example.auth.webauthn.config.WebAuthnConfig
import example.auth.webauthn.core.{UserService, WebAuthnServiceImpl}

/**
 * WebAuthn Server application - Main entry point Supports discoverable passkey
 * authentication with both username-based and usernameless authentication flows
 */
object WebAuthnServer extends ZIOAppDefault {

  override val run = {
    val program = for {
      config  <- ZIO.config(WebAuthnConfig.config)
      _       <- printBanner(config)
      service <- ZIO.service[WebAuthnServiceImpl]
      routes = WebAuthnRoutes(service)
      _ <- Server.serve(routes)
    } yield ()

    program.provide(
      UserService.live,
      WebAuthnServiceImpl.layer,
      Server.default
    )
  }

  private def printBanner(config: WebAuthnConfig) = {
    for {
      _ <- Console.printLine("=" * 60)
      _ <- Console.printLine("WebAuthn Server")
      _ <- Console.printLine("=" * 60)
      _ <- Console.printLine(s"Relying Party ID: ${config.rpId}")
      _ <- Console.printLine(s"Relying Party Name: ${config.rpName}")
      _ <- Console.printLine(s"Origin: ${config.rpOrigin}")
      _ <- Console.printLine("")
      _ <- Console.printLine("Supports both:")
      _ <- Console.printLine("- Username-based authentication")
      _ <- Console.printLine("- Usernameless authentication (discoverable passkeys)")
      _ <- Console.printLine("=" * 60)
    } yield ()
  }
}
