package zhttp.http

import CookieDecoder.log
import zhttp.service.Log
import zio.Duration

import java.security.MessageDigest
import java.util.Base64.getEncoder
import java.util.concurrent.TimeUnit
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import scala.collection.mutable

final case class Cookie[T](name: String, content: String, target: T) { self =>

  /**
   * Gets the cookie's Domain attribute.
   */
  def domain(implicit ev: T =:= Cookie.Response): Option[String] = target.domain

  /**
   * Encodes the cookie to a string.
   */
  def encode(validate: Boolean)(implicit ev: CookieEncoder[T]): Either[Exception, String] =
    try {
      Right(ev.unsafeEncode(self, validate))
    } catch {
      case e: Exception =>
        log.error("Cookie encoding failure", e)
        Left(e)
    }

  /**
   * Gets the cookie's Expires attribute.
   */
  def encode(implicit ev: CookieEncoder[T]): Either[Exception, String] = encode(validate = false)

  /**
   * Returns true if the cookie is allowed over https only
   */
  def isHttpOnly(implicit ev: T =:= Cookie.Response): Boolean = target.isHttpOnly

  /**
   * Returns true if the cookie is allowed over secure connections
   */
  def isSecure(implicit ev: T =:= Cookie.Response): Boolean = target.isSecure

  def maxAge(implicit ev: T =:= Cookie.Response): Option[Duration] =
    target.maxAge.map(long => Duration(long, TimeUnit.SECONDS))

  /**
   * Returns the path of the cookie
   */
  def path(implicit ev: T =:= Cookie.Response): Option[Path] = target.path

  /**
   * Returns the same-site setting for this cookie.
   */
  def sameSite(implicit ev: T =:= Cookie.Response): Option[Cookie.SameSite] = target.sameSite

  /**
   * Signs cookie content with a secret and returns a signed cookie.
   */
  def sign(secret: String)(implicit ev: T =:= Cookie.Response): Cookie[T] =
    withContent(
      new mutable.StringBuilder()
        .append(content)
        .append('.')
        .append(Cookie.signature(secret, content))
        .result(),
    )

  /**
   * Converts cookie to a request cookie.
   */
  def toRequest: RequestCookie = {
    self.target match {
      case _: Cookie.Request => self.asInstanceOf[RequestCookie]
      case _                 => Cookie(name, content, Cookie.Request)
    }
  }

  /**
   * Converts cookie to a response cookie.
   */
  def toResponse: ResponseCookie =
    self.target match {
      case _: Cookie.Response => self.asInstanceOf[ResponseCookie]
      case _                  => self.copy(target = Cookie.Response())
    }

  /**
   * Un-signs cookie content with a secret and returns an unsigned cookie.
   */
  def unSign(secret: String)(implicit ev: T =:= Cookie.Request): Option[Cookie[T]] = {
    val index     = content.lastIndexOf('.')
    val signature = content.slice(index + 1, content.length)
    val value     = content.slice(0, index)
    if (Cookie.signature(secret, value) == signature) Some(self.withContent(value)) else None
  }

  /**
   * Sets content in cookie
   */
  def withContent(content: String): Cookie[T] = copy(content = content)

  /**
   * Sets domain in cookie
   */
  def withDomain(domain: String)(implicit ev: T =:= Cookie.Response): ResponseCookie =
    response(_.copy(domain = Some(domain)))

  /**
   * Sets httpOnly in cookie
   */
  def withHttpOnly(httpOnly: Boolean)(implicit ev: T =:= Cookie.Response): ResponseCookie =
    response(_.copy(isHttpOnly = httpOnly))

  /**
   * Sets httpOnly in cookie
   */
  def withHttpOnly(implicit ev: T =:= Cookie.Response): ResponseCookie =
    withHttpOnly(true)

  /**
   * Sets maxAge of the cookie
   */
  def withMaxAge(maxAge: Duration)(implicit ev: T =:= Cookie.Response): ResponseCookie =
    response(_.copy(maxAge = Some(maxAge.getSeconds)))

  /**
   * Sets maxAge of the cookie
   */
  def withMaxAge(seconds: Long)(implicit ev: T =:= Cookie.Response): ResponseCookie =
    response(_.copy(maxAge = Some(seconds)))

  /**
   * Sets name of the cookie
   */
  def withName(name: String): Cookie[T] = copy(name = name)

  /**
   * Sets path of the cookie
   */
  def withPath(path: Path)(implicit ev: T =:= Cookie.Response): ResponseCookie =
    response(_.copy(path = Some(path)))

  /**
   * Sets sameSite of the cookie
   */
  def withSameSite(sameSite: Cookie.SameSite)(implicit ev: T =:= Cookie.Response): ResponseCookie =
    response(_.copy(sameSite = Some(sameSite)))

  /**
   * Sets secure flag of the cookie
   */
  def withSecure(secure: Boolean)(implicit ev: T =:= Cookie.Response): ResponseCookie =
    response(_.copy(isSecure = secure))

  /**
   * Sets secure flag of the cookie
   */
  def withSecure(implicit ev: T =:= Cookie.Response): ResponseCookie =
    withSecure(true)

  private def response(f: Cookie.Response => Cookie.Response)(implicit
    ev: T =:= Cookie.Response,
  ): ResponseCookie =
    self.copy(target = f(toResponse.target))
}

object Cookie {
  def apply(name: String, content: String): ResponseCookie = Cookie(name, content, Response())

  /**
   * Creates a cookie with an expired maxAge
   */
  def clear(name: String): ResponseCookie = Cookie(name, "").withMaxAge(Long.MinValue)

  /**
   * Creates a cookie from a string.
   */
  def decode[S](string: String, validate: Boolean = false)(implicit ev: CookieDecoder[S]): Either[Exception, ev.Out] = {
    try {
      Right(ev.unsafeDecode(string, validate))
    } catch {
      case e: Exception =>
        log.error("Cookie decoding failure", e)
        Left(e)
    }
  }

  private def signature(secret: String, content: String): String = {
    val sha256    = Mac.getInstance("HmacSHA256")
    val secretKey = new SecretKeySpec(secret.getBytes(), "RSA")

    sha256.init(secretKey)

    val signed = sha256.doFinal(content.getBytes())
    val mda    = MessageDigest.getInstance("SHA-512")
    getEncoder.encodeToString(mda.digest(signed))
  }

  private[http] val log = Log.withTags("Cookie")

  type Request = Request.type
  case object Request

  final case class Response(
    domain: Option[String] = None,
    path: Option[Path] = None,
    isSecure: Boolean = false,
    isHttpOnly: Boolean = false,
    maxAge: Option[Long] = None,
    sameSite: Option[SameSite] = None,
  )

  sealed trait SameSite
  object SameSite {
    case object Strict extends SameSite
    case object Lax    extends SameSite
    case object None   extends SameSite

    def values: List[SameSite] = List(Strict, Lax, None)
  }
}
