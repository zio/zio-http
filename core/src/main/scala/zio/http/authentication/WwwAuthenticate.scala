package zio.http.authentication

import zio.http.authentication.{ AuthenticationScheme, Realm }
import zio.http.Charset

final case class WwwAuthenticate(
  scheme: AuthenticationScheme,
  realm: Realm,
  parameters: Map[String, String],
  charset: Charset
) {
  override def toString = s"WWW-Authenticate: $scheme $realm, charset=${charset.value}"
}
