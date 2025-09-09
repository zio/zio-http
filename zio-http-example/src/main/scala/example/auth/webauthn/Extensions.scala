package example.auth.webauthn
import zio._

object Extensions {

  trait WebAuthnExtension {
    def identifier: String
    def processClientInput(input: Any): IO[Throwable, Any]
    def processAuthenticatorInput(input: Any): IO[Throwable, Array[Byte]]
  }

  case object AppIdExtension extends WebAuthnExtension {
    val identifier = "appid"

    def processClientInput(input: Any): IO[Throwable, Any] = input match {
      case appId: String =>
        // Validate AppID format and authorization
        ZIO.succeed(appId)
      case _             =>
        ZIO.fail(new IllegalArgumentException("AppID must be a string"))
    }

    def processAuthenticatorInput(input: Any): IO[Throwable, Array[Byte]] = {
      // Convert to CBOR for authenticator
      ZIO.succeed(Array.empty[Byte]) // Placeholder
    }
  }

  case object CredPropsExtension extends WebAuthnExtension {
    val identifier = "credProps"

    def processClientInput(input: Any): IO[Throwable, Any] = input match {
      case true => ZIO.succeed(true)
      case _    => ZIO.fail(new IllegalArgumentException("credProps input must be true"))
    }

    def processAuthenticatorInput(input: Any): IO[Throwable, Array[Byte]] = {
      ZIO.succeed(Array.empty[Byte]) // No authenticator input for credProps
    }
  }

  def getSupportedExtensions: Map[String, WebAuthnExtension] = Map(
    AppIdExtension.identifier     -> AppIdExtension,
    CredPropsExtension.identifier -> CredPropsExtension,
  )
}
