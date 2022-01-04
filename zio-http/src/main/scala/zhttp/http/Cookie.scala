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
  private val fieldExpires  = "expires="
  private val fieldMaxAge   = "max-age="
  private val fieldDomain   = "domain="
  private val fieldPath     = "path="
  private val fieldSecure   = "secure"
  private val fieldHttpOnly = "httponly"
  private val fieldSameSite = "samesite="

  private val sameSiteLax    = "lax"
  private val sameSiteStrict = "strict"
  private val sameSiteNone   = "none"

  sealed trait SameSite {
    def asString: String
  }
  object SameSite       {
    case object Lax    extends SameSite { def asString = "Lax"    }
    case object Strict extends SameSite { def asString = "Strict" }
    case object None   extends SameSite { def asString = "None"   }
  }

  /**
   * Decodes from Set-Cookie header value inside of Response into a cookie
   */
  def decodeResponseCookie(headerValue: String): Option[Cookie] =
    Try(unsafeDecodeResponseCookie(headerValue)).toOption

  private[zhttp] def unsafeDecodeResponseCookie(headerValue: String): Cookie = {
    var name: String              = null
    var content: String           = null
    var expires: Instant          = null
    var maxAge: Option[Long]      = None
    var domain: String            = null
    var path: Path                = null
    var secure: Boolean           = false
    var httpOnly: Boolean         = false
    var sameSite: Cookie.SameSite = null

    val headerLength = headerValue.length

    // iterate over all cookie fields (until next semicolon)
    var curr = 0
    var next = 0

    while (next >= 0 && curr < headerLength) {
      next = headerValue.indexOf(';', curr)
      if (next < 0) {
        next = headerLength
      }

      // skip whitespaces one by one to avoid trim allocations
      if (headerValue.charAt(curr) == ' ') {
        curr = curr + 1
      } else {
        // decode name and content first
        if (name == null) {
          val splitIndex = headerValue.indexOf('=', curr)
          if (splitIndex >= 0 && splitIndex < next) {
            name = headerValue.substring(0, splitIndex)
            content = headerValue.substring(splitIndex + 1, next)
          } else {
            name = headerValue.substring(0, next)
          }
        } else if (headerValue.regionMatches(true, curr, fieldExpires, 0, fieldExpires.length)) {
          expires = Instant.parse(headerValue.substring(curr + 8, next))
        } else if (headerValue.regionMatches(true, curr, fieldMaxAge, 0, fieldMaxAge.length)) {
          maxAge = Some(headerValue.substring(curr + 8, next).toLong)
        } else if (headerValue.regionMatches(true, curr, fieldDomain, 0, fieldDomain.length)) {
          domain = headerValue.substring(curr + 7, next)
        } else if (headerValue.regionMatches(true, curr, fieldPath, 0, fieldPath.length)) {
          val v = headerValue.substring(curr + 5, next)
          if (!v.isEmpty) {
            path = Path(v)
          }
        } else if (headerValue.regionMatches(true, curr, fieldSecure, 0, fieldSecure.length)) {
          secure = true
        } else if (headerValue.regionMatches(true, curr, fieldHttpOnly, 0, fieldHttpOnly.length)) {
          httpOnly = true
        } else if (headerValue.regionMatches(true, curr, fieldSameSite, 0, fieldSameSite.length)) {
          if (headerValue.regionMatches(true, curr + 9, sameSiteLax, 0, sameSiteLax.length)) {
            sameSite = SameSite.Lax
          } else if (headerValue.regionMatches(true, curr + 9, sameSiteStrict, 0, sameSiteStrict.length)) {
            sameSite = SameSite.Strict
          } else if (headerValue.regionMatches(true, curr + 9, sameSiteNone, 0, sameSiteNone.length)) {
            sameSite = SameSite.None
          }
        }

        curr = next + 1
      }
    }

    if ((name != null && !name.isEmpty) || (content != null && !content.isEmpty))
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
      )
    else
      null
  }

  /**
   * Decodes from `Cookie` header value inside of Request into a cookie
   */
  def decodeRequestCookie(headerValue: String): Option[List[Cookie]] = {
    val cookies: Array[String]  = headerValue.split(';').map(_.trim)
    val x: List[Option[Cookie]] = cookies.toList.map(a => {
      val (name, content) = splitNameContent(a)
      if (name.isEmpty && content.isEmpty) None
      else Some(Cookie(name, content))
    })

    if (x.contains(None))
      None
    else Some(x.map(_.get))
  }

  @inline
  private def splitNameContent(str: String): (String, String) = {
    val i = str.indexOf('=')
    if (i >= 0) {
      (str.substring(0, i).trim, str.substring(i + 1).trim)
    } else {
      (str.trim, null)
    }
  }

}
