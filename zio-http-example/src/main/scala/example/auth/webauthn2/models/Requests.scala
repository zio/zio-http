package example.auth.webauthn2.models

import com.yubico.webauthn.data._
import zio.json._
import zio.json.ast._

/**
 * Request DTOs for WebAuthn operations
 */
case class RegistrationStartRequest(username: String)
object RegistrationStartRequest {
  implicit val decoder: JsonDecoder[RegistrationStartRequest] = DeriveJsonDecoder.gen
}

case class RegistrationFinishRequest(
  username: String,
  userhandle: String,
  publicKeyCredential: PublicKeyCredential[AuthenticatorAttestationResponse, ClientRegistrationExtensionOutputs],
)

object RegistrationFinishRequest {
  implicit val decoder: JsonDecoder[RegistrationFinishRequest] =
    JsonDecoder[Json].mapOrFail { o =>
      for {
        u   <- o.get(JsonCursor.field("username")).flatMap(_.as[String])
        uh  <- o.get(JsonCursor.field("userhandle")).flatMap(_.as[String])
        pkc <- o
          .get(JsonCursor.field("publicKeyCredential"))
          .map(_.toString())
          .map(PublicKeyCredential.parseRegistrationResponseJson)
      } yield RegistrationFinishRequest(u, uh, pkc)
    }
}

case class AuthenticationStartRequest(username: Option[String])

object AuthenticationStartRequest {
  implicit val decoder: JsonDecoder[AuthenticationStartRequest] = DeriveJsonDecoder.gen
}

case class AuthenticationFinishRequest(
  username: Option[String], // Optional for discoverable passkeys
  publicKeyCredential: PublicKeyCredential[AuthenticatorAssertionResponse, ClientAssertionExtensionOutputs],
)

object AuthenticationFinishRequest {
  implicit val decoder: JsonDecoder[AuthenticationFinishRequest] =
    JsonDecoder[Json].mapOrFail { o =>
      for {
        u   <-
          Right(
            o.get(JsonCursor.field("username"))
              .toOption
              .flatMap(_.as[String].toOption),
          )
        pkc <- o
          .get(JsonCursor.field("publicKeyCredential"))
          .map(_.toString())
          .map(PublicKeyCredential.parseAssertionResponseJson)
      } yield AuthenticationFinishRequest(u, pkc)
    }
}
