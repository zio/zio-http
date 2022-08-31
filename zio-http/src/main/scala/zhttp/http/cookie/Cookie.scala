package zhttp.http.cookie

import zhttp.http.Path
import zio.Duration

final case class Cookie[T](name: String, content: String, target: T) { self =>
  def client(f: Cookie.Response => Cookie.Response)(implicit ev: T =:= Cookie.Response): ResponseCookie =
    self.copy(target = f(toResponse.target))

  def domain(implicit ev: T =:= Cookie.Response): Option[String] = target.domain

  def encode(strict: Boolean)(implicit ev: CookieEncoder[T]): Either[Exception, String] =
    try {
      Right(ev.encode(self, strict))
    } catch {
      case e: Exception => Left(e)
    }

  def encode(implicit ev: CookieEncoder[T]): Either[Exception, String] = encode(strict = false)

  def isHttpOnly(implicit ev: T =:= Cookie.Response): Boolean = target.isHttpOnly

  def isSecure(implicit ev: T =:= Cookie.Response): Boolean = target.isSecure

  def maxAge(implicit ev: T =:= Cookie.Response): Option[Duration] = target.maxAge

  def path(implicit ev: T =:= Cookie.Response): Option[Path] = target.path

  def sameSite(implicit ev: T =:= Cookie.Response): Option[Cookie.SameSite] = target.sameSite

  def sign(secret: String)(implicit ev: T =:= Cookie.Response): Cookie[T] = ???

  def toRequest: RequestCookie = Cookie(name, content, Cookie.Request)

  def toResponse: ResponseCookie =
    self.target match {
      case _: Cookie.Response => self.asInstanceOf[ResponseCookie]
      case _                  => self.copy(target = Cookie.Response())
    }

  def unsign(secret: String)(implicit ev: T =:= Cookie.Response): Cookie[T] = ???

  def verify(secret: String)(implicit ev: T =:= Cookie.Request): Boolean = ???

  def withContent(content: String): Cookie[T] = copy(content = content)

  def withDomain(domain: String)(implicit ev: T =:= Cookie.Response): ResponseCookie =
    client(_.copy(domain = Some(domain)))

  def withHttpOnly(httpOnly: Boolean)(implicit ev: T =:= Cookie.Response): ResponseCookie =
    client(_.copy(isHttpOnly = httpOnly))

  def withMaxAge(maxAge: Duration)(implicit ev: T =:= Cookie.Response): ResponseCookie =
    client(_.copy(maxAge = Some(maxAge)))

  def withName(name: String): Cookie[T] = copy(name = name)

  def withPath(path: Path)(implicit ev: T =:= Cookie.Response): ResponseCookie =
    client(_.copy(path = Some(path)))

  def withSameSite(sameSite: Cookie.SameSite)(implicit ev: T =:= Cookie.Response): ResponseCookie =
    client(_.copy(sameSite = Some(sameSite)))

  def withSecure(secure: Boolean)(implicit ev: T =:= Cookie.Response): ResponseCookie =
    client(_.copy(isSecure = secure))
}

object Cookie {
  def apply(name: String, content: String): RequestCookie = Cookie(name, content, Request)

  type Request = Request.type
  case object Request

  final case class Response(
    domain: Option[String] = None,
    path: Option[Path] = None,
    isSecure: Boolean = false,
    isHttpOnly: Boolean = false,
    maxAge: Option[Duration] = None,
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
