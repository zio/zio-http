package example.auth.webauthn.model

import zio.json._

case class RegistrationStartRequest(username: String)

object RegistrationStartRequest {
  implicit val codec: JsonCodec[RegistrationStartRequest] = DeriveJsonCodec.gen
}
