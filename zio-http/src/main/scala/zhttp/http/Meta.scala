package zhttp.http

import zhttp.http.SameSite.site

import java.util.Date

final case class Meta(
  expires: Option[Date] = None,
  domain: Option[String] = None,
  path: Option[Path] = None,
  secure: Boolean = false,
  httpOnly: Boolean = false,
  maxAge: Option[Long] = None,
  sameSite: Option[SameSite] = None,
)

object Meta {
  import java.text.SimpleDateFormat
  import java.util.TimeZone

  val df = new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss zzz", java.util.Locale.US)
  df.setTimeZone(TimeZone.getTimeZone("GMT"))

  private def metaOption[S](m: Option[S], v: String): String = m match {
    case Some(value) => s"; $v=$value"
    case None        => ""
  }

  private def metaBool(m: Boolean, v: String) = if (m) s"; $v=true" else ""

  private def format(maybeDate: Option[Date]): Option[String] = maybeDate match {
    case Some(value) => Some(df.format(value))
    case None        => None
  }

  def getMeta[M <: Meta](meta: Option[M]) = meta match {
    case Some(value) =>
      s"""${metaOption(format(value.expires), "Expires")}${metaOption(value.path, "Path")}
        ${metaOption(value.domain, "Domain")}${metaOption(value.maxAge, "Max-Age")}
        ${metaOption(site(value.sameSite), "SameSite")}
        ${metaBool(value.secure, "Secure")}
        ${metaBool(value.httpOnly, "HttpOnly")}"""
    case None        => ""
  }
}
