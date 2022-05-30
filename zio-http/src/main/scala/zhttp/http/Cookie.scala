package zhttp.http

import zio.duration._

import java.security.MessageDigest
import java.time.Instant
import java.util.Base64.getEncoder
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
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
  secret: Option[String] = None,
) { self =>

  /**
   * Creates a new cookie that can be used to clear the original cookie on the
   * client.
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
   * Signs the cookie at the time of encoding using the provided secret.
   */
  def sign(secret: String): Cookie = copy(secret = Some(secret))

  /**
   * Removes secret in the cookie
   */
  def unSign: Cookie = copy(secret = None)

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
    val c = secret match {
      case Some(sec) if sec.nonEmpty => content + "." + signContent(sec)
      case _                         => content
    }

    val cookie = List(
      Some(s"$name=$c"),
      expires.map(e => s"Expires=$e"),
      maxAge.map(a => s"Max-Age=${a.toString}"),
      domain.filter(_.nonEmpty).map(d => s"Domain=$d"),
      path.filter(_.nonEmpty).map(p => s"Path=${p.encode}"),
      if (isSecure) Some("Secure") else None,
      if (isHttpOnly) Some("HttpOnly") else None,
      sameSite.map(s => s"SameSite=${s.asString}"),
    )
    cookie.flatten.mkString("; ")
  }

  /**
   * Signs cookie content with a secret and returns signature
   */
  private def signContent(secret: String): String = {
    val sha256    = Mac.getInstance("HmacSHA256")
    val secretKey = new SecretKeySpec(secret.getBytes(), "RSA")
    sha256.init(secretKey)
    val signed    = sha256.doFinal(self.content.getBytes())
    val mda       = MessageDigest.getInstance("SHA-512")
    getEncoder.encodeToString(mda.digest(signed))
  }

  /**
   * Verifies signed-cookie's signature with a secret
   */
  private def verify(content: String, signature: String, secret: String): Boolean =
    self.withContent(content).signContent(secret) == signature

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
  def decodeResponseCookie(headerValue: String, secret: Option[String] = None): Option[Cookie] =
    Try(unsafeDecodeResponseCookie(headerValue, secret)).toOption

  private[zhttp] def unsafeDecodeResponseCookie(headerValue: String, secret: Option[String] = None): Cookie = {
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
          if (v.nonEmpty) {
            path = Path.decode(v)
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
    val decodedCookie =
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
        Cookie(
          "",
          "",
          expires = Option(expires),
          maxAge = maxAge,
          domain = Option(domain),
          path = Option(path),
          isSecure = secure,
          isHttpOnly = httpOnly,
          sameSite = Option(sameSite),
        )

    secret match {
      case Some(s) if s.nonEmpty => {
        if (decodedCookie != null) {
          val index     = decodedCookie.content.lastIndexOf('.')
          val signature = decodedCookie.content.slice(index + 1, decodedCookie.content.length)
          val content   = decodedCookie.content.slice(0, index)

          if (decodedCookie.verify(content, signature, s))
            decodedCookie.withContent(content).sign(s)
          else null
        } else decodedCookie
      }
      case _                     => decodedCookie.copy(secret = secret)
    }

  }

  /**
   * Decodes from `Cookie` header value inside of Request into a cookie
   */
  def decodeRequestCookie(headerValue: String): List[Cookie] = {
    if (headerValue.nonEmpty) {
      val cookies: Array[String]  = headerValue.split(';').map(_.trim)
      val x: List[Option[Cookie]] = cookies.toList.map(a => {
        val (name, content) = splitNameContent(a)
        if (name.isEmpty && content.isEmpty) Some(Cookie("", ""))
        else Some(Cookie(name, content))
      })

      if (x.contains(None))
        List.empty
      else x.map(_.get)
    } else List.empty
  }

  @inline
  private def splitNameContent(str: String): (String, String) = {
    val i = str.indexOf('=')
    if (i >= 0) {
      (str.substring(0, i).trim, str.substring(i + 1).trim)
    } else {
      (str.trim, "")
    }
  }

}
