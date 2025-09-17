package example.auth.webauthn2.models

import com.yubico.webauthn.data.{AuthenticatorAssertionResponse, AuthenticatorAttestationResponse, ClientAssertionExtensionOutputs, ClientRegistrationExtensionOutputs, PublicKeyCredential}
import zio.Chunk
import zio.json.ast.{Json, JsonCursor}
import zio.schema.Schema
import zio.schema.codec.{BinaryCodec, DecodeError}
import zio.stream.ZPipeline

/**
 * Request DTOs for WebAuthn operations
 */

// Registration request DTOs
case class RegistrationStartRequest(username: String)

case class RegistrationFinishRequest(
  username: String,
  publicKeyCredential: PublicKeyCredential[AuthenticatorAttestationResponse, ClientRegistrationExtensionOutputs],
)

object RegistrationFinishRequest {
  import zio.json._
  implicit val decoder: JsonDecoder[RegistrationFinishRequest] =
    JsonDecoder[Json].mapOrFail { o =>
      for {
        u   <- o.get(JsonCursor.field("username")).flatMap(_.as[String])
        pkc <- o
          .get(JsonCursor.field("publicKeyCredential"))
          .map(_.toString())
          .map(PublicKeyCredential.parseRegistrationResponseJson)
      } yield RegistrationFinishRequest(u, pkc)
    }
}

case class AttestationResponse(
  clientDataJSON: String,
  attestationObject: String,
)

// Authentication request DTOs
case class AuthenticationStartRequest(username: Option[String])

case class AuthenticationFinishRequest(
  username: Option[String], // Optional for discoverable passkeys
  publicKeyCredential: PublicKeyCredential[AuthenticatorAssertionResponse, ClientAssertionExtensionOutputs],
)

object AuthenticationFinishRequest {
  import zio.json._
  implicit val decoder: JsonDecoder[AuthenticationFinishRequest] =
    JsonDecoder[Json].mapOrFail { o =>
      for {
        u   <- o.get(JsonCursor.field("username")).flatMap(_.as[Option[String]])
        pkc <- o
          .get(JsonCursor.field("publicKeyCredential"))
          .map(_.toString())
          .map(PublicKeyCredential.parseAssertionResponseJson)
      } yield AuthenticationFinishRequest(u, pkc)
    }
}

case class AssertionResponse(
  clientDataJSON: String,
  authenticatorData: String,
  signature: String,
  userHandle: Option[String],
)

// Client data structure for parsing authentication responses
case class ClientData(challenge: String, origin: String, `type`: String)
