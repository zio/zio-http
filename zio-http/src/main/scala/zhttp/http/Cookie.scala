package zhttp.http

import java.util.Date

sealed trait SameSite
object SameSite {
  case object Strict extends SameSite
  case object Lax    extends SameSite
  case object None   extends SameSite
}

final case class Meta(
  expires: Option[Date] = None,
  domain: Option[String] = None,
  path: Option[Path] = None,
  secure: Boolean = false,
  httpOnly: Boolean = false,
  maxAge: Option[Long] = None,
  sameSite: Option[SameSite] = None,
)
final case class Cookie[M <: Meta](name: String, content: String, meta: Option[M] = None) { self =>

  import java.text.SimpleDateFormat
  import java.util.TimeZone

  val df = new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss zzz", java.util.Locale.US)
  df.setTimeZone(TimeZone.getTimeZone("GMT"))

  def clearCookie: Cookie[Nothing] =
    copy(content = "", meta = None)

  private def metaOption[S](m: Option[S], v: String): String = m match {
    case Some(value) => s"; $v=$value"
    case None        => ""
  }

  private def metaBool(m: Boolean, v: String) = if (m) s"; $v=true" else ""

  private def site(sameSite: Option[SameSite]): Option[String] = sameSite match {
    case Some(site) =>
      site match {
        case SameSite.Strict => Some("Strict")
        case SameSite.Lax    => Some("Lax")
        case SameSite.None   => Some("None")
      }
    case None       => None
  }
  private def format(maybeDate: Option[Date]): Option[String]  = maybeDate match {
    case Some(value) => Some(df.format(value))
    case None        => None
  }
  private def meta(meta: Option[M])                            = meta match {
    case Some(value) =>
      s"""${metaOption(format(value.expires), "Expires")}${metaOption(value.path, "Path")}
        ${metaOption(value.domain, "Domain")}${metaOption(value.maxAge, "Max-Age")}
        ${metaOption(site(value.sameSite), "SameSite")}
        ${metaBool(value.secure, "Secure")}
        ${metaBool(value.httpOnly, "HttpOnly")}"""
    case None        => ""
  }
  def fromCookie: String                                       = s"${self.name}=${self.content}${meta(self.meta)}"

}
object Cookie {
  def toCookie(header: Header): Cookie[Nothing] = {
    val list    = header.value.toString.split(";").toList.head.split("=")
    val name    = list(0)
    val content = list(1)
    Cookie(name, content)
  }
  def toCookieList(header: Header): List[Cookie[Nothing]] = {
    val list: List[String] = header.value.toString.split(";").toList
    list.map(a => {
      val list = a.split("=").toList
      Cookie(list.head, list.tail.head)
    })
  }
}
