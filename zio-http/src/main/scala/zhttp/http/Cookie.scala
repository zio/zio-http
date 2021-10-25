package zhttp.http

import java.time.Instant
import scala.concurrent.duration.{Duration, SECONDS}
import scala.util.{Failure, Success, Try}

final case class Cookie(
  name: String,
  content: String,
  expires: Option[Instant] = None,
  domain: Option[String] = None,
  path: Option[Path] = None,
  secure: Boolean = false,
  httpOnly: Boolean = false,
  maxAge: Option[Duration] = None,
  sameSite: Option[Cookie.SameSite] = None,
) { self =>

  /**
   * remove cookie helper
   */
  def clearCookie =
    copy(content = "", expires = Some(Instant.ofEpochSecond(0)))

  /**
   * helpers for setting cookie values
   */
  def setContent(v: String): Cookie           = copy(content = v)
  def setExpires(v: Instant): Cookie          = copy(expires = Some(v))
  def setMaxAge(v: Duration): Cookie          = copy(maxAge = Some(v))
  def setDomain(v: String): Cookie            = copy(domain = Some(v))
  def setPath(v: Path): Cookie                = copy(path = Some(v))
  def setSecure(): Cookie                     = copy(secure = true)
  def setHttpOnly(): Cookie                   = copy(httpOnly = true)
  def setSameSite(v: Cookie.SameSite): Cookie = copy(sameSite = Some(v))

  /**
   * helpers for removing cookie values
   */
  def resetSecure(): Cookie    = copy(secure = false)
  def resetHttpOnly(): Cookie  = copy(httpOnly = false)
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
      expires.map(e => s"Expires=$e"),
      maxAge.map(a => s"Max-Age=${a.toSeconds}"),
      domain.map(d => s"Domain=$d"),
      path.map(p => s"Path=${p.asString}"),
      if (secure) Some("Secure") else None,
      if (httpOnly) Some("HttpOnly") else None,
      sameSite.map(s => s"SameSite=${s.asString}"),
    )
    cookie.flatten.mkString("; ")
  }

}

object Cookie {

  /**
   * Parse cookie
   */
  private[zhttp] def fromString(headerValue: String): Either[Throwable, Cookie] = {
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
      if (name.trim == "" && content.isEmpty) Left(new IllegalArgumentException("Cookie can't be parsed"))
      else Right(Cookie(name, content.getOrElse("")))

    other.map(splitNameContent).map(t => (t._1.toLowerCase, t._2)).foreach {
      case ("expires", Some(v))  =>
        parseDate(v) match {
          case Left(_)      => cookie = Left(new IllegalArgumentException("expiry date cannot be parsed"))
          case Right(value) => cookie = cookie.map(_.setExpires(value))
        }
      case ("max-age", Some(v))  =>
        Try(v.toLong) match {
          case Success(maxAge) => cookie = cookie.map(_.setMaxAge(Duration(maxAge, SECONDS)))
          case Failure(_)      => cookie = Left(new IllegalArgumentException("max-age cannot be parsed"))
        }
      case ("domain", v)         => cookie = cookie.map(_.setDomain(v.getOrElse("")))
      case ("path", v)           => cookie = cookie.map(_.setPath(Path(v.getOrElse(""))))
      case ("secure", _)         => cookie = cookie.map(_.setSecure())
      case ("httponly", _)       => cookie = cookie.map(_.setHttpOnly())
      case ("samesite", Some(v)) =>
        v.trim.toLowerCase match {
          case "lax"    => cookie = cookie.map(_.setSameSite(SameSite.Lax))
          case "strict" => cookie = cookie.map(_.setSameSite(SameSite.Strict))
          case "none"   => cookie = cookie.map(_.setSameSite(SameSite.None))
          case _        => None
        }
      case (_, _)                => cookie
    }
    cookie
  }

  def parseDate(v: String): Either[String, Instant] =
    Try(Instant.parse(v)) match {
      case Success(r) => Right(r)
      case Failure(e) => Left(s"Invalid http date: $v (${e.getMessage})")
    }

  sealed trait SameSite {
    def asString: String
  }
  object SameSite       {
    case object Lax    extends SameSite { def asString = "Lax"    }
    case object Strict extends SameSite { def asString = "Strict" }
    case object None   extends SameSite { def asString = "None"   }
  }

}
