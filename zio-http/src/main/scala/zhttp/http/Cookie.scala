package zhttp.http

import zio.duration._

import java.security.MessageDigest
import java.time.Instant
import java.util.Base64.getEncoder
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import scala.util.Try

/**
 * Defines the fields and methods of a http cookie.
 */
trait Cookie extends CookieBuilder { self =>
  def name: String
  def content: String
  def expires: Option[Instant]
  def domain: Option[String]
  def path: Option[Path]
  def isSecure: Boolean
  def isHttpOnly: Boolean
  def maxAge: Option[Long]
  def sameSite: Option[Cookie.SameSite]
  def secret: Option[String]

  def encode: String = {
    val c      = secret match {
      case Some(sec) => content + "." + signContent(sec)
      case None      => content
    }
    val cookie = List(
      Some(s"$name=$c"),
      expires.map(e => s"Expires=$e"),
      maxAge.map(a => s"Max-Age=${a.toString}"),
      domain.map(d => s"Domain=$d"),
      path.map(p => s"Path=${p.encode}"),
      if (isSecure) Some("Secure") else None,
      if (isHttpOnly) Some("HttpOnly") else None,
      sameSite.map(s => s"SameSite=${s.asString}"),
    )
    cookie.flatten.mkString("; ")
  }

  override def clone: CookieImpl =
    CookieImpl(
      name = self.name,
      content = self.content,
      expires = self.expires,
      domain = self.domain,
      path = self.path,
      isSecure = self.isSecure,
      isHttpOnly = self.isHttpOnly,
      maxAge = self.maxAge,
      sameSite = self.sameSite,
    )

  /**
   * Signs cookie content with a secret and returns signature
   */
  protected def signContent(secret: String): String = {
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
  protected def verify(content: String, signature: String, secret: String): Boolean =
    self.withContent(content).signContent(secret) == signature
}

/**
 * Field based cookie impl
 */
final case class CookieImpl(
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
) extends Cookie

/**
 * Methods to mutate a Cookie
 */
trait CookieBuilder { self: Cookie =>

  /**
   * Creates a new cookie that can be used to clear the original cookie on the
   * client.
   */
  def clear: Cookie = self.clone.copy(content = "", expires = Some(Instant.ofEpochSecond(0)))

  /**
   * Sets content in cookie
   */
  def withContent(v: String): Cookie = self.clone.copy(content = v)

  /**
   * Sets expiry in cookie
   */
  def withExpiry(v: Instant): Cookie = self.clone.copy(expires = Some(v))

  /**
   * Sets max-age in cookie
   */
  def withMaxAge(v: Duration): Cookie = self.clone.copy(maxAge = Some(v.asScala.toSeconds))

  /**
   * Sets max-age in seconds in cookie
   */
  def withMaxAge(v: Long): Cookie = self.clone.copy(maxAge = Some(v))

  /**
   * Sets domain in cookie
   */
  def withDomain(v: String): Cookie = self.clone.copy(domain = Some(v))

  /**
   * Sets path in cookie
   */
  def withPath(v: Path): Cookie = self.clone.copy(path = Some(v))

  /**
   * Sets secure in cookie
   */
  def withSecure: Cookie = self.clone.copy(isSecure = true)

  /**
   * Sets httpOnly in cookie
   */
  def withHttpOnly: Cookie = self.clone.copy(isHttpOnly = true)

  /**
   * Sets same-site in cookie
   */
  def withSameSite(v: Cookie.SameSite): Cookie = self.clone.copy(sameSite = Some(v))

  /**
   * Resets secure flag in the cookie
   */
  def withoutSecure: Cookie = self.clone.copy(isSecure = false)

  /**
   * Signs the cookie at the time of encoding using the provided secret.
   */
  def sign(secret: String): Cookie = self.clone.copy(secret = Some(secret))

  /**
   * Removes secret in the cookie
   */
  def unSign: Cookie = self.clone.copy(secret = None)

  /**
   * Resets httpOnly flag in the cookie
   */
  def withoutHttpOnly: Cookie = self.clone.copy(isHttpOnly = false)

  /**
   * Removes expiry from the cookie
   */
  def withoutExpiry: Cookie = self.clone.copy(expires = None)

  /**
   * Removes domain from the cookie
   */
  def withoutDomain: Cookie = self.clone.copy(domain = None)

  /**
   * Removes path from the cookie
   */
  def withoutPath: Cookie = self.clone.copy(path = None)

  /**
   * Removes max-age from the cookie
   */
  def withoutMaxAge: Cookie = self.clone.copy(maxAge = None)

  /**
   * Removes same-site from the cookie
   */
  def withoutSameSite: Cookie = self.clone.copy(sameSite = None)
}

/**
 * Indexed fields cookie view
 */
case class CookieView(cookie: String) extends Cookie {
  case class FieldIndex(start: Int, end: Int)

  def name: String =
    nameIndex.map(f => cookie.substring(f.start, f.end).trim).getOrElse("")

  def content: String =
    valueIndex.map(f => cookie.substring(f.start, f.end).trim).getOrElse("")

  def expires: Option[Instant] =
    expiresIndex.flatMap(f => Try(Instant.parse(cookie.substring(f.start, f.end).trim)).toOption)

  def domain: Option[String] =
    domainIndex.map(f => cookie.substring(f.start, f.end))

  def path: Option[Path] =
    pathIndex.map(f => Path(cookie.substring(f.start, f.end)))

  def isSecure: Boolean = secureValue

  def isHttpOnly: Boolean = httpOnlyValue

  def maxAge: Option[Long] =
    maxAgeIndex.flatMap(f => cookie.substring(f.start, f.end).toLongOption)

  def sameSite: Option[Cookie.SameSite] = sameSiteValue

  def secret: Option[String] = None

  private var nameIndex: Option[FieldIndex]    = None
  private var valueIndex: Option[FieldIndex]   = None
  private var expiresIndex: Option[FieldIndex] = None
  private var domainIndex: Option[FieldIndex]  = None
  private var maxAgeIndex: Option[FieldIndex]  = None
  private var pathIndex: Option[FieldIndex]    = None

  private var secureValue: Boolean                   = false
  private var httpOnlyValue: Boolean                 = false
  private var sameSiteValue: Option[Cookie.SameSite] = None

  private def parseIndex(str: String, from: Option[FieldIndex] = None): Option[FieldIndex] =
    from match {
      case Some(FieldIndex(_, end)) if end == str.length =>
        None
      case Some(FieldIndex(_, end))                      =>
        val next = str.indexOf(';', end + 1)
        Some(FieldIndex(end + 1, if (next < 0) str.length else next))
      case None                                          =>
        val next = str.indexOf('=')
        Some(FieldIndex(0, if (next >= 0) next else str.length))
    }

  private val fieldExpires   = "expires="
  private val fieldMaxAge    = "max-age="
  private val fieldDomain    = "domain="
  private val fieldPath      = "path="
  private val fieldSecure    = "secure"
  private val fieldHttpOnly  = "httponly"
  private val fieldSameSite  = "samesite="
  private val sameSiteLax    = "lax"
  private val sameSiteStrict = "strict"
  private val sameSiteNone   = "none"

  private def initializeFieldIndexes(): Unit =
    if (cookie != null && !cookie.isBlank()) {
      nameIndex = parseIndex(cookie)
      valueIndex = parseIndex(cookie, nameIndex)

      val headerLength = cookie.length
      var curr         = valueIndex.map(_.end).getOrElse(0)
      var next         = curr

      while (next >= 0 && curr < headerLength) {
        next = cookie.indexOf(';', curr)
        if (next < 0) {
          next = headerLength
        }

        // skip whitespaces one by one to avoid trim allocations
        if (cookie.charAt(curr) == ' ') {
          curr = curr + 1
        } else {
          // decode name and content first
          if (cookie.regionMatches(true, curr, fieldExpires, 0, fieldExpires.length)) {
            expiresIndex = Some(FieldIndex(curr + fieldExpires.length, next))
          } else if (cookie.regionMatches(true, curr, fieldMaxAge, 0, fieldMaxAge.length)) {
            maxAgeIndex = Some(FieldIndex(curr + fieldMaxAge.length, next))
          } else if (cookie.regionMatches(true, curr, fieldDomain, 0, fieldDomain.length)) {
            domainIndex = Some(FieldIndex(curr + fieldDomain.length, next))
          } else if (cookie.regionMatches(true, curr, fieldPath, 0, fieldPath.length)) {
            pathIndex = Some(FieldIndex(curr + fieldPath.length, next))
          } else if (cookie.regionMatches(true, curr, fieldSecure, 0, fieldSecure.length)) {
            secureValue = true
          } else if (cookie.regionMatches(true, curr, fieldHttpOnly, 0, fieldHttpOnly.length)) {
            httpOnlyValue = true
          } else if (cookie.regionMatches(true, curr, fieldSameSite, 0, fieldSameSite.length)) {
            if (cookie.regionMatches(true, curr + 9, sameSiteLax, 0, sameSiteLax.length)) {
              sameSiteValue = Some(Cookie.SameSite.Lax)
            } else if (cookie.regionMatches(true, curr + 9, sameSiteStrict, 0, sameSiteStrict.length)) {
              sameSiteValue = Some(Cookie.SameSite.Strict)
            } else if (cookie.regionMatches(true, curr + 9, sameSiteNone, 0, sameSiteNone.length)) {
              sameSiteValue = Some(Cookie.SameSite.None)
            }
          }

          curr = next + 1
        }
      }
    }

  initializeFieldIndexes()
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

  def apply(
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
  ): Cookie = CookieImpl(name, content, expires, domain, path, isSecure, isHttpOnly, maxAge, sameSite, secret)

  /**
   * Decodes from Set-Cookie header value inside of Response into a cookie
   */
  def decodeResponseCookie(headerValue: String, secret: Option[String] = None): Option[Cookie] =
    Try(unsafeDecodeResponseCookie(headerValue, secret)).toOption

  private[zhttp] def unsafeDecodeResponseCookie(headerValue: String, secret: Option[String] = None): Cookie = {
    val cookie = CookieView(headerValue)

    secret match {
      case Some(s) =>
        val index     = cookie.content.lastIndexOf('.')
        val signature = cookie.content.slice(index + 1, cookie.content.length)
        val content   = cookie.content.slice(0, index)

        if (cookie.verify(content, signature, s))
          cookie.withContent(content).sign(s)
        else
          null
      case None    => cookie
    }
  }

  /**
   * Decodes from `Cookie` header value inside of Request into a cookie
   */
  def decodeRequestCookie(headerValue: String): Option[List[Cookie]] = {
    val cookies: Array[String]  = headerValue.split(';').map(_.trim)
    val x: List[Option[Cookie]] = cookies.toList.map { a =>
      val c = CookieView(a)
      if (c.name.isEmpty && c.content.isEmpty) None
      else Some(c)
    }

    if (x.contains(None))
      None
    else Some(x.map(_.get))
  }
}
