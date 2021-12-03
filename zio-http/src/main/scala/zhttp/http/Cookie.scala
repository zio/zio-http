package zhttp.http

import zio.duration._

import java.time.Instant
import scala.util.Try

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
   * Creates a new cookie that can be used to clear the original cookie on the client.
   */
  def clear: Cookie =
    copy(content = "", expires = Some(Instant.ofEpochSecond(0)))

  /**
   * Sets content in cookie
   */
  def withContent(v: String): Cookie = copy(content = v)

  /**
   * Sets expiry in cookie
   */
  def withExpiry(v: Instant): Cookie = copy(expires = Some(v))

  /**
   * Sets max-age in cookie
   */
  def withMaxAge(v: Duration): Cookie = copy(maxAge = Some(v.asScala.toSeconds))

  /**
   * Sets max-age in seconds in cookie
   */
  def withMaxAge(v: Long): Cookie = copy(maxAge = Some(v))

  /**
   * Sets domain in cookie
   */
  def withDomain(v: String): Cookie = copy(domain = Some(v))

  /**
   * Sets path in cookie
   */
  def withPath(v: Path): Cookie = copy(path = Some(v))

  /**
   * Sets secure in cookie
   */
  def withSecure: Cookie = copy(isSecure = true)

  /**
   * Sets httpOnly in cookie
   */
  def withHttpOnly: Cookie = copy(isHttpOnly = true)

  /**
   * Sets same-site in cookie
   */
  def withSameSite(v: Cookie.SameSite): Cookie = copy(sameSite = Some(v))

  /**
   * Resets secure flag in the cookie
   */
  def withoutSecure: Cookie = copy(isSecure = false)

  /**
   * Resets httpOnly flag in the cookie
   */
  def withoutHttpOnly: Cookie = copy(isHttpOnly = false)

  /**
   * Removes expiry from the cookie
   */
  def withoutExpiry: Cookie = copy(expires = None)

  /**
   * Removes domain from the cookie
   */
  def withoutDomain: Cookie = copy(domain = None)

  /**
   * Removes path from the cookie
   */
  def withoutPath: Cookie = copy(path = None)

  /**
   * Removes max-age from the cookie
   */
  def withoutMaxAge: Cookie = copy(maxAge = None)

  /**
   * Removes same-site from the cookie
   */
  def withoutSameSite: Cookie = copy(sameSite = None)

  /**
   * Converts cookie into a string
   */
  def encode: String = {
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
   * Decodes from Set-Cookie header value inside of Response into a cookie
   */
  def decodeResponseCookie(headerValue: String): Option[Cookie] = {
    var name: String              = null
    var content: String           = null
    var expires: Instant          = null
    var maxAge: Option[Long]      = None
    var domain: String            = null
    var path: Path                = null
    var secure: Boolean           = false
    var httpOnly: Boolean         = false
    var sameSite: Cookie.SameSite = null

    var prev = 0
    var i    = 0
    while (i >= 0) {
      i = headerValue.indexOf(';', prev)
      val c       = if (i <= 0) headerValue.substring(prev) else headerValue.substring(prev, i)
      val (n, ct) = splitNameContent(c)

      (n.toLowerCase, ct) match {
        case ("expires", v)                => expires = parseDate(v).getOrElse(null)
        case ("max-age", v)                => maxAge = Try(v.toLong).toOption
        case ("domain", v)                 => domain = v
        case ("path", v)                   => path = if (!v.isEmpty) Path(v) else null
        case ("secure", _)                 => secure = true
        case ("httponly", _)               => httpOnly = true
        case ("samesite", v)               =>
          v.trim.toLowerCase match {
            case "lax"    => sameSite = SameSite.Lax
            case "strict" => sameSite = SameSite.Strict
            case "none"   => sameSite = SameSite.None
            case _        => ()
          }
        case (_, aContent) if name == null =>
          name = n
          content = aContent
        case (_, _)                        => ()
      }
      prev = i + 1
    }

    if (name != "" || content != "")
      Some(
        Cookie(
          name = name,
          content = content,
          expires = Option(expires),
          maxAge = maxAge,
          domain = Option(domain),
          path = Option(path),
          isSecure = secure,
          isHttpOnly = httpOnly,
          sameSite = Option(sameSite),
        ),
      )
    else
      None
  }

  /**
   * Decodes from `Cookie` header value inside of Request into a cookie
   */
  def decodeRequestCookie(headerValue: String): Option[List[Cookie]] = {
    val cookies: Array[String]  = headerValue.split(";").map(_.trim)
    val x: List[Option[Cookie]] = cookies.toList.map(a => {
      val (name, content) = splitNameContent(a)
      if (name == "" && content.isEmpty) None
      else Some(Cookie(name, content))
    })

    if (x.contains(None))
      None
    else Some(x.map(_.get))
  }

  @inline
  private def parseDate(v: String): Try[Instant] =
    Try(Instant.parse(v))

  @inline
  private def splitNameContent(str: String): (String, String) = {
    val i = str.indexOf('=')
    if (i >= 0) {
      (str.substring(0, i).trim, str.substring(i + 1).trim)
    } else {
      (str.trim, null)
    }
  }

  /**
   * Updates maxAge in cookie
   */
  def maxAge(maxAge: Duration): Update = Update(_.withMaxAge(maxAge))

  /**
   * Updates domain in cookie
   */
  def domain(domain: String): Update = Update(_.withDomain(domain))

  /**
   * Updates expiry in cookie
   */
  def expiry(expires: Instant): Update = Update(_.withExpiry(expires))

  /**
   * Updates path in cookie
   */
  def path(path: Path): Update = Update(_.withPath(path))

  /**
   * Updates secure in cookie
   */
  def secure: Update = Update(_.withSecure)

  /**
   * Updates httpOnly in cookie
   */
  def httpOnly: Update = Update(_.withHttpOnly)

  /**
   * Updates sameSite in cookie
   */
  def sameSite(sameSite: SameSite): Update = Update(_.withSameSite(sameSite))
}
