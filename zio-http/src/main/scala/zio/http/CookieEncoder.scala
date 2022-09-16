package zio.http

import io.netty.handler.codec.http.{cookie => jCookie}
import zio.http.model.Cookie.SameSite
import zio.http.model.{Cookie, Request, Response}

sealed trait CookieEncoder[A] {
  final def apply(a: Cookie[A]): String = unsafeEncode(a, false)
  def unsafeEncode(a: Cookie[A], validate: Boolean): String
}

object CookieEncoder {
  implicit object RequestCookieEncoder extends CookieEncoder[Request] {
    override def unsafeEncode(cookie: Cookie[Request], validate: Boolean): String = {
      val encoder = if (validate) jCookie.ClientCookieEncoder.STRICT else jCookie.ClientCookieEncoder.LAX
      val builder = new jCookie.DefaultCookie(cookie.name, cookie.content)
      encoder.encode(builder)
    }
  }

  implicit object ResponseCookieEncoder extends CookieEncoder[Response] {
    override def unsafeEncode(cookie: Cookie[Response], validate: Boolean): String = {
      val builder = new jCookie.DefaultCookie(cookie.name, cookie.content)

      val encoder = if (validate) jCookie.ServerCookieEncoder.STRICT else jCookie.ServerCookieEncoder.LAX

      cookie.domain.foreach(builder.setDomain)
      cookie.path.foreach(i => builder.setPath(i.encode))
      cookie.maxAge.foreach(i => builder.setMaxAge(i.getSeconds))
      cookie.sameSite.foreach {
        case SameSite.Strict => builder.setSameSite(jCookie.CookieHeaderNames.SameSite.Strict)
        case SameSite.Lax    => builder.setSameSite(jCookie.CookieHeaderNames.SameSite.Lax)
        case SameSite.None   => builder.setSameSite(jCookie.CookieHeaderNames.SameSite.None)
      }

      builder.setHttpOnly(cookie.isHttpOnly)
      builder.setSecure(cookie.isSecure)

      encoder.encode(builder)
    }
  }
}
