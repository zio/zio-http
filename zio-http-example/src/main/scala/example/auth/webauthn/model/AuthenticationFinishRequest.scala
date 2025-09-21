package example.auth.webauthn.model

import com.yubico.webauthn.data._
import zio.json._
import zio.json.ast._

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
