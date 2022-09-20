package zio.http.model

import zio.http._
import zio.http.model.Cookie.{SameSite, Type}
import zio.{Duration, Unsafe}

import java.security.MessageDigest
import java.util.Base64.getEncoder
import java.util.concurrent.TimeUnit
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import scala.collection.mutable
import zio.stacktracer.TracingImplicits.disableAutoTrace // scalafix:ok;

/**
 * Cookie is an immutable and type-safe representation of an HTTP cookie. It can
 * be of type types viz. `Cookie[Request]` and `Cookie[Response]`.
 * `Cookie[Request]` is only available in the `Request` object and
 * `Cookie[Response]` is only available in the `Response` object.
 */
final case class Cookie[T](name: String, content: String, target: Cookie.Type[T]) { self =>

  /**
   * Gets the cookie's Domain attribute.
   */
  def domain(implicit ev: T =:= Response): Option[String] =
    target.asResponse.domain

  /**
   * Encodes the cookie to a string.
   */
  def encode(validate: Boolean)(implicit ev: CookieEncoder[T]): Either[Exception, String] =
    try {
      Right(ev.unsafe.encode(self, validate)(Unsafe.unsafe))
    } catch {
      case e: Exception =>
        Left(e)
    }

  /**
   * Gets the cookie's Expires attribute.
   */
  def encode(implicit ev: CookieEncoder[T]): Either[Exception, String] = encode(validate = false)

  /**
   * Returns true if the cookie is allowed over https only
   */
  def isHttpOnly(implicit ev: T =:= Response): Boolean = target.asResponse.isHttpOnly

  /**
   * Returns true if the cookie is allowed over secure connections
   */
  def isSecure(implicit ev: T =:= Response): Boolean = target.asResponse.isSecure

  def maxAge(implicit ev: T =:= Response): Option[Duration] =
    target.asResponse.maxAge.map(long => Duration(long, TimeUnit.SECONDS))

  /**
   * Returns the path of the cookie
   */
  def path(implicit ev: T =:= Response): Option[Path] = target.asResponse.path

  /**
   * Returns the same-site setting for this cookie.
   */
  def sameSite(implicit ev: T =:= Response): Option[Cookie.SameSite] = target.asResponse.sameSite

  /**
   * Signs cookie content with a secret and returns a signed cookie.
   */
  def sign(secret: String)(implicit ev: T =:= Response): Cookie[T] =
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
  def toRequest: Cookie[Request] = Cookie(name, content, Type.request)

  /**
   * Converts cookie to a response cookie.
   */
  def toResponse: Cookie[Response] = toResponse()

  /**
   * Converts cookie to a response cookie.
   */
  def toResponse(
    domain: Option[String] = None,
    path: Option[Path] = None,
    isSecure: Boolean = false,
    isHttpOnly: Boolean = false,
    maxAge: Option[Long] = None,
    sameSite: Option[SameSite] = None,
  ): Cookie[Response] = {
    self.target match {
      case _: Type.RequestType.type  =>
        Cookie(name, content, Type.response(domain, path, isSecure, isHttpOnly, maxAge, sameSite))
      case target: Type.ResponseType => Cookie(name, content, target: Cookie.Type[Response])
    }
  }

  /**
   * Un-signs cookie content with a secret and returns an unsigned cookie.
   */
  def unSign(secret: String)(implicit ev: T =:= Request): Option[Cookie[T]] = {
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
  def withDomain(domain: String)(implicit ev: T =:= Response): Cookie[Response] =
    update(_.copy(domain = Some(domain)))

  /**
   * Sets httpOnly in cookie
   */
  def withHttpOnly(httpOnly: Boolean)(implicit ev: T =:= Response): Cookie[Response] =
    update(_.copy(isHttpOnly = httpOnly))

  /**
   * Sets httpOnly in cookie
   */
  def withHttpOnly(implicit ev: T =:= Response): Cookie[Response] =
    withHttpOnly(true)

  /**
   * Sets maxAge of the cookie
   */
  def withMaxAge(maxAge: Duration)(implicit ev: T =:= Response): Cookie[Response] =
    update(_.copy(maxAge = Some(maxAge.getSeconds)))

  /**
   * Sets maxAge of the cookie
   */
  def withMaxAge(seconds: Long)(implicit ev: T =:= Response): Cookie[Response] =
    update(_.copy(maxAge = Some(seconds)))

  /**
   * Sets name of the cookie
   */
  def withName(name: String): Cookie[T] = copy(name = name)

  /**
   * Sets path of the cookie
   */
  def withPath(path: Path)(implicit ev: T =:= Response): Cookie[Response] =
    update(_.copy(path = Some(path)))

  /**
   * Sets sameSite of the cookie
   */
  def withSameSite(sameSite: Cookie.SameSite)(implicit ev: T =:= Response): Cookie[Response] =
    update(_.copy(sameSite = Some(sameSite)))

  /**
   * Sets secure flag of the cookie
   */
  def withSecure(secure: Boolean)(implicit ev: T =:= Response): Cookie[Response] =
    update(_.copy(isSecure = secure))

  /**
   * Sets secure flag of the cookie
   */
  def withSecure(implicit ev: T =:= Response): Cookie[Response] =
    withSecure(true)

  private def update(f: Type.ResponseType => Type.ResponseType)(implicit ev: T =:= Response): Cookie[Response] =
    self.copy(target = f(toResponse.target.asResponse))
}

object Cookie {

  /**
   * Creates a new cookie of response type
   */
  def apply(
    name: String,
    content: String,
    domain: Option[String] = None,
    path: Option[Path] = None,
    isSecure: Boolean = false,
    isHttpOnly: Boolean = false,
    maxAge: Option[Long] = None,
    sameSite: Option[SameSite] = None,
  ): Cookie[Response] =
    Cookie(name, content, Type.response(domain, path, isSecure, isHttpOnly, maxAge, sameSite))

  /**
   * Creates a cookie with an expired maxAge
   */
  def clear(name: String): Cookie[Response] = Cookie(name, "").toResponse.withMaxAge(Long.MinValue)

  /**
   * Creates a cookie from a string.
   */
  def decode[S](string: String, validate: Boolean = false)(implicit ev: CookieDecoder[S]): Either[Exception, ev.Out] = {
    try {
      Right(ev.unsafe.decode(string, validate)(Unsafe.unsafe))
    } catch {
      case e: Exception =>
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

  sealed trait Type[A] extends Product with Serializable {
    def asResponse(implicit ev: A =:= Response): Type.ResponseType =
      this.asInstanceOf[Type.ResponseType]
  }

  object Type {
    case object RequestType extends Type[Request]

    final case class ResponseType(
      domain: Option[String] = None,
      path: Option[Path] = None,
      isSecure: Boolean = false,
      isHttpOnly: Boolean = false,
      maxAge: Option[Long] = None,
      sameSite: Option[SameSite] = None,
    ) extends Type[Response]

    def request: Type[Request] = RequestType
    def response(
      domain: Option[String] = None,
      path: Option[Path] = None,
      isSecure: Boolean = false,
      isHttpOnly: Boolean = false,
      maxAge: Option[Long] = None,
      sameSite: Option[SameSite] = None,
    ): Type[Response] = ResponseType(domain, path, isSecure, isHttpOnly, maxAge, sameSite)
  }

  sealed trait SameSite
  object SameSite {
    case object Strict extends SameSite
    case object Lax    extends SameSite
    case object None   extends SameSite

    def values: List[SameSite] = List(Strict, Lax, None)
  }
}
