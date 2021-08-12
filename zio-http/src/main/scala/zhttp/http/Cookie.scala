package zhttp.http

import java.text.SimpleDateFormat
import java.util.{Date, TimeZone}

sealed trait SameSite
object SameSite {
  case object Lax    extends SameSite { def asString = "Lax"    }
  case object Strict extends SameSite { def asString = "Strict" }
  case object None   extends SameSite { def asString = "None"   }
}

final case class Cookie(
  name: String,
  content: String,
  expires: Option[Date] = None,
  domain: Option[String] = None,
  path: Option[Path] = None,
  secure: Boolean = false,
  httpOnly: Boolean = false,
  maxAge: Option[Long] = None,
  sameSite: Option[SameSite] = None,
) { self =>
  def clearCookie =
    copy(content = "")

  def setContent(v: String): Cookie            = copy(content = v)
  def setExpires(v: Option[Date]): Cookie      = copy(expires = v)
  def setMaxAge(v: Option[Long]): Cookie       = copy(maxAge = v)
  def setDomain(v: Option[String]): Cookie     = copy(domain = v)
  def setPath(v: Option[Path]): Cookie         = copy(path = v)
  def setSecure(v: Boolean): Cookie            = copy(secure = v)
  def setHttpOnly(v: Boolean): Cookie          = copy(httpOnly = v)
  def setSameSite(s: Option[SameSite]): Cookie = copy(sameSite = s)

  def removeExpiry(): Cookie   = copy(expires = None)
  def removeDomain(): Cookie   = copy(domain = None)
  def removePath(): Cookie     = copy(path = None)
  def removeMaxAge(): Cookie   = copy(maxAge = None)
  def removeSameSite(): Cookie = copy(sameSite = None)

  val df = new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss zzz", java.util.Locale.US)
  df.setTimeZone(TimeZone.getTimeZone("GMT"))

  def asString: String = {
    val cookie = List(
      Some(s"$name=$content"),
      expires.map(e => s"Expires=${df.format(e)}"),
      maxAge.map(a => s"Max-Age=$a"),
      domain.map(d => s"Domain=$d"),
      path.map(p => s"Path=$p"),
      if (secure) Some("Secure") else None,
      if (httpOnly) Some("HttpOnly") else None,
      sameSite.map(s => s"SameSite=$s"),
    )
    cookie.flatten.mkString("; ")
  }

}

object Cookie {

  /**
   * Parse the cookie
   */
  def fromString(headerValue: String): Cookie = {
    def splitNameContent(kv: String): (String, Option[String]) =
      kv.split("=", 2).map(_.trim) match {
        case Array(v1)     => (v1, None)
        case Array(v1, v2) => (v1, Some(v2))
        case _             => ("", None)
      }

    val cookie          = headerValue.split(";").map(_.trim)
    val (first, _)      = (cookie.head, cookie.tail)
    val (name, content) = splitNameContent(first)
    Cookie(name, content.getOrElse(""))
  }
}
