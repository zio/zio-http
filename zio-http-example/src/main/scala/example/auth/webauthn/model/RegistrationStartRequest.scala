package example.auth.webauthn.model

import com.yubico.webauthn.data._
import zio.json._

case class RegistrationStartRequest(username: String)

object RegistrationStartRequest {
  implicit val codec: JsonCodec[RegistrationStartRequest] = DeriveJsonCodec.gen
}

