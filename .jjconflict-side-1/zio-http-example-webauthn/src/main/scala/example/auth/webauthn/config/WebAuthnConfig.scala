package example.auth.webauthn.config

import zio._
import zio.config._

case class WebAuthnConfig(
  rpId: String,
  rpName: String,
  rpOrigin: String,
)

object WebAuthnConfig {
  val config: Config[WebAuthnConfig] = (
    Config.string("rp-id").withDefault("localhost") ++
      Config.string("rp-name").withDefault("WebAuthn Demo") ++
      Config.string("rp-origin").withDefault("http://localhost:8080")
  ).to[WebAuthnConfig]
}
