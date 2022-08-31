package zhttp.http.cookie

import zio.Duration
import io.netty.handler.codec.http.{cookie => jCookie}

import java.time.Instant

final case class Cookie[T](name: String, content: String, target: T) { self =>
  def domain(implicit ev: T =:= Cookie.Client): Option[String] = target.domain

  def expires(implicit ev: T =:= Cookie.Client): Option[Instant] = target.expires

  def isHttpOnly(implicit ev: T =:= Cookie.Client): Boolean = target.isHttpOnly

  def isSecure(implicit ev: T =:= Cookie.Client): Boolean = target.isSecure

  def maxAge(implicit ev: T =:= Cookie.Client): Option[Duration] = target.maxAge

  def path(implicit ev: T =:= Cookie.Client): Option[String] = target.path

  def sameSite(implicit ev: T =:= Cookie.Client): Option[Cookie.SameSite] = target.sameSite

  def sign(secret: String)(implicit ev: T =:= Cookie.Client): Cookie[T] = ???

  def toJava: jCookie.Cookie = ???

  def toRequest: Cookie[Cookie.Server] = target match {
    case _: Cookie.Server => self.asInstanceOf[Cookie[Cookie.Server]]
    case _                => Cookie(name, content, Cookie.Server)
  }

  def toResponse: Cookie[Cookie.Client] = target match {
    case _: Cookie.Client => self.asInstanceOf[Cookie[Cookie.Client]]
    case _                => Cookie(name, content, Cookie.Client())
  }

  def unsign(secret: String)(implicit ev: T =:= Cookie.Client): Cookie[T] = ???

  def verify(secret: String)(implicit ev: T =:= Cookie.Server): Boolean = ???

  def withContent(content: String)(implicit ev: T =:= Cookie.Client): Cookie[T] = ???

  def withDomain(domain: String)(implicit ev: T =:= Cookie.Client): Cookie[T] = ???

  def withExpires(expires: Instant)(implicit ev: T =:= Cookie.Client): Cookie[T] = ???

  def withHttpOnly(httpOnly: Boolean)(implicit ev: T =:= Cookie.Client): Cookie[T] = ???

  def withMaxAge(maxAge: Duration)(implicit ev: T =:= Cookie.Client): Cookie[T] = ???

  def withPath(path: String)(implicit ev: T =:= Cookie.Client): Cookie[T] = ???

  def withSameSite(sameSite: Cookie.SameSite)(implicit ev: T =:= Cookie.Client): Cookie[T] = ???

  def withSecure(secure: Boolean)(implicit ev: T =:= Cookie.Client): Cookie[T] = ???
}

object Cookie {

  def apply(name: String, content: String): Cookie[Unit] = Cookie(name, content, ())

  type Server = Server.type
  case object Server

  final case class Client(
    expires: Option[Instant] = None,
    domain: Option[String] = None,
    path: Option[String] = None,
    isSecure: Boolean = false,
    isHttpOnly: Boolean = false,
    maxAge: Option[Duration] = None,
    sameSite: Option[Cookie.SameSite] = None,
  )

  sealed trait SameSite
  object SameSite {
    case object Strict extends SameSite
    case object Lax    extends SameSite

    def values: List[SameSite] = List(Strict, Lax)
  }
}
