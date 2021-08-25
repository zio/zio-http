package zhttp.http

import java.time.format.DateTimeFormatter
import java.time.{Instant, ZoneId}
import scala.concurrent.duration.{Duration, SECONDS}
import scala.util.{Failure, Success, Try}

sealed trait SameSite
object SameSite {
  case object Lax    extends SameSite { def asString = "Lax"    }
  case object Strict extends SameSite { def asString = "Strict" }
  case object None   extends SameSite { def asString = "None"   }
}

final case class Cookie(
  name: String,
  content: String,
  expires: Option[Instant] = None,
  domain: Option[String] = None,
  path: Option[String] = None,
  secure: Boolean = false,
  httpOnly: Boolean = false,
  maxAge: Option[Duration] = None,
  sameSite: Option[SameSite] = None,
) { self =>

  /**
   * remove cookie helper
   */
  def clearCookie =
    copy(content = "", expires = Some(Instant.ofEpochSecond(0)))

  /**
   * helpers for setting cookie values
   */
  def setContent(v: String): Cookie    = copy(content = v)
  def setExpires(v: Instant): Cookie   = copy(expires = Some(v))
  def setMaxAge(v: Duration): Cookie   = copy(maxAge = Some(v))
  def setDomain(v: String): Cookie     = copy(domain = Some(v))
  def setPath(v: String): Cookie       = copy(path = Some(v))
  def setSecure(v: Boolean): Cookie    = copy(secure = v)
  def setHttpOnly(v: Boolean): Cookie  = copy(httpOnly = v)
  def setSameSite(v: SameSite): Cookie = copy(sameSite = Some(v))

  /**
   * helpers for removing cookie values
   */
  def removeExpiry(): Cookie   = copy(expires = None)
  def removeDomain(): Cookie   = copy(domain = None)
  def removePath(): Cookie     = copy(path = None)
  def removeMaxAge(): Cookie   = copy(maxAge = None)
  def removeSameSite(): Cookie = copy(sameSite = None)

  /**
   * Cookie header to String
   */
  def asString: String = {
    val cookie = List(
      Some(s"$name=$content"),
      expires.map(e => s"Expires=${DateTimeFormatter.RFC_1123_DATE_TIME.format(e.atZone(ZoneId.of("GMT")))}"),
      maxAge.map(a => s"Max-Age=${a.toSeconds}"),
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
   * Parse cookie
   */
  private[zhttp] def fromString(headerValue: String): Cookie = {
    def splitNameContent(kv: String): (String, Option[String]) =
      kv.split("=", 2).map(_.trim) match {
        case Array(v1)     => (v1, None)
        case Array(v1, v2) => (v1, Some(v2))
        case _             => ("", None)
      }

    val cookieWithoutMeta = headerValue.split(";").map(_.trim)
    val (first, other)    = (cookieWithoutMeta.head, cookieWithoutMeta.tail)
    val (name, content)   = splitNameContent(first)
    var cookie            =
      if (name.trim == "" && content.isEmpty) throw new IllegalArgumentException("Cookie can't be parsed")
      else Cookie(name, content.getOrElse(""))

    other.map(splitNameContent).map(t => (t._1, t._2)).foreach {
      case (ignoreCase"expires", Some(v))  =>
        parseDate(v) match {
          case Left(_)      => None
          case Right(value) => cookie = cookie.setExpires(value)
        }
      case (ignoreCase"max-age", Some(v))  =>
        Try(v.toLong) match {
          case Success(maxAge) => cookie = cookie.setMaxAge(Duration(maxAge, SECONDS))
          case Failure(_)      => None
        }
      case (ignoreCase"domain", v)         => cookie = cookie.setDomain(v.getOrElse(""))
      case (ignoreCase"path", v)           => cookie = cookie.setPath(v.getOrElse(""))
      case (ignoreCase"secure", _)         => cookie = cookie.setSecure(true)
      case (ignoreCase"httponly", _)       => cookie = cookie.setHttpOnly(true)
      case (ignoreCase"samesite", Some(v)) =>
        v.trim match {
          case ignoreCase"lax"    => cookie = cookie.setSameSite(SameSite.Lax)
          case ignoreCase"strict" => cookie = cookie.setSameSite(SameSite.Strict)
          case ignoreCase"none"   => cookie = cookie.setSameSite(SameSite.None)
          case _                  => None
        }
      case (_, _)                          => cookie
    }
    cookie
  }

  implicit class CaseInsensitiveRegex(sc: StringContext) {
    def ignoreCase = ("(?i)" + sc.parts.mkString).r
  }

  def parseDate(v: String): Either[String, Instant] =
    Try(Instant.from(DateTimeFormatter.RFC_1123_DATE_TIME.parse(v))) match {
      case Success(r) => Right(r)
      case Failure(e) => Left(s"Invalid http date: $v (${e.getMessage})")
    }

}
