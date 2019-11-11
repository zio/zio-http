package zio.http.authentication

import java.util.Base64

final case class Credentials(value: String) extends AnyVal {
  override def toString =
    Base64.getEncoder().encodeToString(value.getBytes())
}
