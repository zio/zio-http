package zhttp.http

import java.time.format.DateTimeFormatter
import java.time.{Instant, ZoneId}
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
  path: Option[Path] = None,
  secure: Boolean = false,
  httpOnly: Boolean = false,
  maxAge: Option[Long] = None,
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
  def setContent(v: String): Cookie            = copy(content = v)
  def setExpires(v: Option[Instant]): Cookie   = copy(expires = v)
  def setMaxAge(v: Option[Long]): Cookie       = copy(maxAge = v)
  def setDomain(v: Option[String]): Cookie     = copy(domain = v)
  def setPath(v: Option[Path]): Cookie         = copy(path = v)
  def setSecure(v: Boolean): Cookie            = copy(secure = v)
  def setHttpOnly(v: Boolean): Cookie          = copy(httpOnly = v)
  def setSameSite(s: Option[SameSite]): Cookie = copy(sameSite = s)

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
    val cookie            =
      if (name.trim == "" && content.isEmpty) throw new IllegalArgumentException("Cookie can't be parsed")
      else Cookie(name, content.getOrElse(""))

    other.map(splitNameContent).map(t => (t._1, t._2)).foreach {
      case (ignoreCase"expires", Some(v))  =>
        cookie.setExpires(parseDate(v) match {
          case Left(_)      => None
          case Right(value) => Some(value)
        })
      case (ignoreCase"max-age", Some(v))  =>
        cookie.setMaxAge(Try(v.toLong) match {
          case Success(maxAge) => Some(maxAge)
          case Failure(_)      => None
        })
      case (ignoreCase"domain", v)         => cookie.setDomain(Some(v.getOrElse("")))
      case (ignoreCase"path", v)           => cookie.setPath(Some(Path(v.getOrElse(""))))
      case (ignoreCase"secure", _)         => cookie.setSecure(true)
      case (ignoreCase"httponly", _)       => cookie.setHttpOnly(true)
      case (ignoreCase"samesite", Some(v)) =>
        v.trim match {
          case ignoreCase"lax"    => cookie.setSameSite(Some(SameSite.Lax))
          case ignoreCase"strict" => cookie.setSameSite(Some(SameSite.Strict))
          case ignoreCase"none"   => cookie.setSameSite(Some(SameSite.None))
          case _                  => cookie.setSameSite(None)
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
