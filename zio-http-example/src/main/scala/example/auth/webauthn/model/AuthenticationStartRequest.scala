package example.auth.webauthn.model

import zio.json._
case class AuthenticationStartRequest(username: Option[String])

object AuthenticationStartRequest {
  implicit val codec: JsonCodec[AuthenticationStartRequest] = DeriveJsonCodec.gen
}
