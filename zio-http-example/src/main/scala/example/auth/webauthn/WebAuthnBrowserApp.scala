package example.auth.webauthn

import zio._
import zio.http._

object WebAuthnBrowserApp extends ZIOAppDefault {

  def run = {
    val serverConfig = Server.Config.default.port(8080)

    val app = WebAuthnRoutes.routes

    Server
      .serve(app)
      .provide(
        Server.defaultWithPort(8080),
        WebAuthnService.live,
        MobileDeviceManager.live,
      )
      .orDie

  }
}
