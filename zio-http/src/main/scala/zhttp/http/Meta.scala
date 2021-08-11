package zhttp.http

import java.text.SimpleDateFormat
import java.util.{Date, TimeZone}

final case class Meta(
  expires: Option[Date] = None,
  domain: Option[String] = None,
  path: Option[Path] = None,
  secure: Boolean = false,
  httpOnly: Boolean = false,
  maxAge: Option[Long] = None,
  sameSite: Option[SameSite] = None,
) {

  val df = new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss zzz", java.util.Locale.US)
  df.setTimeZone(TimeZone.getTimeZone("GMT"))

  override def toString: String = {
    val meta = List(
      expires.map(e => s"Expires=${df.format(e)}"),
      maxAge.map(a => s"Max-Age=$a"),
      domain.map(d => s"Domain=$d"),
      path.map(p => s"Path=$p"),
      if (secure) Some("Secure") else None,
      if (httpOnly) Some("HttpOnly") else None,
      sameSite.map(s => s"SameSite=$s"),
    )
    meta.flatten.mkString("; ")
  }
}
object Meta {}
