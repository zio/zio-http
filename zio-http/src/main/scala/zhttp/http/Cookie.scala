package zhttp.http

import zio.duration._

import java.time.Instant
import scala.util.{Failure, Success, Try}

final case class Cookie(
  name: String,
  content: String,
  expires: Option[Instant] = None,
  domain: Option[String] = None,
  path: Option[Path] = None,
  isSecure: Boolean = false,
  isHttpOnly: Boolean = false,
  maxAge: Option[Long] = None,
  sameSite: Option[Cookie.SameSite] = None,
) { self =>

  /**
   * Helper method to create cookies
   */
  def @@(update: Cookie.Update): Cookie = update.f(self)

  /**
   * Clears the cookie with empty content
   */
  def clearCookie: Cookie =
    copy(content = "", expires = Some(Instant.ofEpochSecond(0)))

  /**
   * Set content in cookie
   */
  def setContent(v: String): Cookie = copy(content = v)

  /**
   * Set expiry in cookie
   */
  def setExpiry(v: Instant): Cookie = copy(expires = Some(v))

  /**
   * Set max-age in cookie
   */
  def setMaxAge(v: Duration): Cookie = copy(maxAge = Some(v.asScala.toSeconds))

  /**
   * Set max-age in seconds in cookie
   */
  def setMaxAge(v: Long): Cookie = copy(maxAge = Some(v))

  /**
   * Set domain in cookie
   */
  def setDomain(v: String): Cookie = copy(domain = Some(v))

  /**
   * Set path in cookie
   */
  def setPath(v: Path): Cookie = copy(path = Some(v))

  /**
   * Set secure in cookie
   */
  def withSecure: Cookie = copy(isSecure = true)

  /**
   * Set httpOnly in cookie
   */
  def withHttpOnly: Cookie = copy(isHttpOnly = true)

  /**
   * Set same-site in cookie
   */
  def setSameSite(v: Cookie.SameSite): Cookie = copy(sameSite = Some(v))

  /**
   * Reset secure in cookie
   */
  def resetSecure: Cookie = copy(isSecure = false)

  /**
   * Reset httpOnly in cookie
   */
  def resetHttpOnly: Cookie = copy(isHttpOnly = false)

  /**
   * Remove expires in cookie
   */
  def removeExpiry: Cookie = copy(expires = None)

  /**
   * Remove domain in cookie
   */
  def removeDomain: Cookie = copy(domain = None)

  /**
   * Remove path in cookie
   */
  def removePath: Cookie = copy(path = None)

  /**
   * Remove max-age in cookie
   */
  def removeMaxAge: Cookie = copy(maxAge = None)

  /**
   * Remove same-site in cookie
   */
  def removeSameSite: Cookie = copy(sameSite = None)

  /**
   * Cookie header to String
   */
  def asString: String = {
    val cookie = List(
      Some(s"$name=$content"),
      expires.map(e => s"Expires=$e"),
      maxAge.map(a => s"Max-Age=${a.toString}"),
      domain.map(d => s"Domain=$d"),
      path.map(p => s"Path=${p.asString}"),
      if (isSecure) Some("Secure") else None,
      if (isHttpOnly) Some("HttpOnly") else None,
      sameSite.map(s => s"SameSite=${s.asString}"),
    )
    cookie.flatten.mkString("; ")
  }

}

object Cookie {

  sealed trait SameSite {
    def asString: String
  }
  object SameSite       {
    case object Lax    extends SameSite { def asString = "Lax"    }
    case object Strict extends SameSite { def asString = "Strict" }
    case object None   extends SameSite { def asString = "None"   }
  }
  case class Update(f: Cookie => Cookie)

  /**
   * Parse cookie
   */
  private[zhttp] def parse(headerValue: String): Either[Throwable, Cookie] = {
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
          case Right(value) => cookie = cookie.map(_ @@ expiry(value))
        }
      case ("max-age", Some(v))  =>
        Try(v.toLong) match {
          case Success(age) => cookie = cookie.map(x => x.setMaxAge(age))
          case Failure(_)   => cookie = Left(new IllegalArgumentException("max-age cannot be parsed"))
        }
      case ("domain", v)         => cookie = cookie.map(_.setDomain(v.getOrElse("")))
      case ("path", v)           => cookie = cookie.map(_.setPath(Path(v.getOrElse(""))))
      case ("secure", _)         => cookie = cookie.map(_.withSecure)
      case ("httponly", _)       => cookie = cookie.map(_.withHttpOnly)
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

  private def parseDate(v: String): Either[String, Instant] =
    Try(Instant.parse(v)) match {
      case Success(r) => Right(r)
      case Failure(e) => Left(s"Invalid http date: $v (${e.getMessage})")
    }

  /**
   * To update maxAge in cookie
   */
  def maxAge(maxAge: Duration): Update = Update(_.setMaxAge(maxAge))

  /**
   * To update domain in cookie
   */
  def domain(domain: String): Update = Update(_.setDomain(domain))

  /**
   * To update expiry in cookie
   */
  def expiry(expires: Instant): Update = Update(_.setExpiry(expires))

  /**
   * To update path in cookie
   */
  def path(path: Path): Update = Update(_.setPath(path))

  /**
   * To update secure in cookie
   */
  def secure: Update = Update(_.withSecure)

  /**
   * To update httpOnly in cookie
   */
  def httpOnly: Update = Update(_.withHttpOnly)

  /**
   * To update sameSite in cookie
   */
  def sameSite(sameSite: SameSite): Update = Update(_.setSameSite(sameSite))
}
