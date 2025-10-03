package example.auth.webauthn.model

import com.yubico.webauthn.data._
import zio.json._
import zio.json.ast.{Json, JsonCursor}

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
