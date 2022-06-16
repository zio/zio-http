package zhttp.http

import zio.duration._

import java.security.MessageDigest
import java.time.Instant
import java.util.Base64.getEncoder
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

final case class Cookie(
  name: String,
  content: String,
  expires: Option[Instant] = None,
  domain: String = "",
  path: Path = Path.empty,
  isSecure: Boolean = false,
  isHttpOnly: Boolean = false,
  maxAge: Option[Long] = None,
  sameSite: Option[Cookie.SameSite] = None,
  secret: String = "",
) { self =>

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

  /**
   * Creates a new cookie that can be used to clear the original cookie on the
   * client.
   */
  def clear: Cookie =
    copy(content = "", expires = Some(Instant.ofEpochSecond(0)))

  /**
   * Converts cookie into a string
   */
  def encode: String = {
    if (name.isEmpty || content.isEmpty) ""
    else {
      val c = secret match {
        case ""  => content
        case sec => content + "." + signContent(sec)
      }

      List(
        Some(s"$name=$c"),
        expires.map(e => s"Expires=$e"),
        maxAge.map(a => s"Max-Age=${a.toString}"),
        if (domain.nonEmpty) Some(s"Domain=$domain") else None,
        if (path.encode.nonEmpty) Some(s"Path=${path.encode}") else None,
        if (isSecure) Some("Secure") else None,
        if (isHttpOnly) Some("HttpOnly") else None,
        sameSite.map(s => s"SameSite=${s.asString}"),
      ).flatten.mkString("; ")
    }
  }

  /**
   * Signs the cookie at the time of encoding using the provided secret.
   */
  def sign(secret: String): Cookie = copy(secret = secret)

  /**
   * Removes secret in the cookie
   */
  def unSign: Cookie = copy(secret = "")

  /**
   * Sets content in cookie
   */
  def withContent(v: String): Cookie = copy(content = v)

  /**
   * Sets domain in cookie
   */
  def withDomain(domain: String): Cookie = copy(domain = domain)

  /**
   * Sets expiry in cookie
   */
  def withExpiry(v: Instant): Cookie = copy(expires = Some(v))

  /**
   * Sets httpOnly in cookie
   */
  def withHttpOnly: Cookie = copy(isHttpOnly = true)

  /**
   * Sets max-age in cookie
   */
  def withMaxAge(v: Duration): Cookie = copy(maxAge = Some(v.asScala.toSeconds))

  /**
   * Sets max-age in seconds in cookie
   */
  def withMaxAge(v: Long): Cookie = copy(maxAge = Some(v))

  /**
   * Sets path in cookie
   */
  def withPath(path: Path): Cookie = copy(path = path)

  /**
   * Sets same-site in cookie
   */
  def withSameSite(v: Cookie.SameSite): Cookie = copy(sameSite = Some(v))

  /**
   * Sets secure in cookie
   */
  def withSecure: Cookie = copy(isSecure = true)

  /**
   * Removes domain from the cookie
   */
  def withoutDomain: Cookie = copy(domain = "")

  /**
   * Removes expiry from the cookie
   */
  def withoutExpiry: Cookie = copy(expires = None)

  /**
   * Resets httpOnly flag in the cookie
   */
  def withoutHttpOnly: Cookie = copy(isHttpOnly = false)

  /**
   * Removes max-age from the cookie
   */
  def withoutMaxAge: Cookie = copy(maxAge = None)

  /**
   * Removes path from the cookie
   */
  def withoutPath: Cookie = copy(path = Path.empty)

  /**
   * Removes same-site from the cookie
   */
  def withoutSameSite: Cookie = copy(sameSite = None)

  /**
   * Resets secure flag in the cookie
   */
  def withoutSecure: Cookie = copy(isSecure = false)

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

  @inline
  private def splitNameContent(str: String): (String, String) = {
    val i = str.indexOf('=')
    if (i >= 0) {
      (str.substring(0, i).trim, str.substring(i + 1).trim)
    } else {
      (str.trim, "")
    }
  }

  private[zhttp] def unsafeDecodeResponseCookie(headerValue: String, secret: String = ""): Cookie = {
    var name: String              = null
    var content: String           = null
    var expires: Instant          = null
    var maxAge: Option[Long]      = None
    var domain: String            = ""
    var path: Path                = Path.empty
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
          domain = domain,
          path = path,
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
          domain = domain,
          path = path,
          isSecure = secure,
          isHttpOnly = httpOnly,
          sameSite = Option(sameSite),
        )

    secret match {
      case ""     => decodedCookie.copy(secret = secret)
      case secret => {
        if (decodedCookie != null) {
          val index     = decodedCookie.content.lastIndexOf('.')
          val signature = decodedCookie.content.slice(index + 1, decodedCookie.content.length)
          val content   = decodedCookie.content.slice(0, index)

          if (decodedCookie.verify(content, signature, secret))
            decodedCookie.withContent(content).sign(secret)
          else null
        } else decodedCookie
      }
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

  /**
   * Decodes from Set-Cookie header value inside of Response into a cookie
   */
  def decodeResponseCookie(headerValue: String, secret: String = ""): Option[Cookie] = {
    try
      Option {
        unsafeDecodeResponseCookie(headerValue, secret)
      }
    catch {
      case _: Throwable => None
    }
  }

  sealed trait SameSite {
    def asString: String
  }

  object SameSite {
    case object Lax    extends SameSite { def asString = "Lax"    }
    case object Strict extends SameSite { def asString = "Strict" }
    case object None   extends SameSite { def asString = "None"   }
  }

}
