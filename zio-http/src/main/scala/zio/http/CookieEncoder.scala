package zio.http

import io.netty.handler.codec.http.{cookie => jCookie}
import zio.Unsafe
import zio.http.model.Cookie
import zio.http.model.Cookie.SameSite
import zio.stacktracer.TracingImplicits.disableAutoTrace // scalafix:ok;

sealed trait CookieEncoder[A] {
  final def apply(a: Cookie[A])(implicit unsafe: Unsafe): String =
    this.unsafe.encode(a, validate = false)

  trait UnsafeAPI {
    def encode(a: Cookie[A], validate: Boolean)(implicit unsafe: Unsafe): String
  }

  val unsafe: UnsafeAPI
}

object CookieEncoder {
  implicit object RequestCookieEncoder extends CookieEncoder[Request] {
    override final val unsafe: UnsafeAPI = new UnsafeAPI {
      override final def encode(cookie: Cookie[Request], validate: Boolean)(implicit unsafe: Unsafe): String = {
        val encoder = if (validate) jCookie.ClientCookieEncoder.STRICT else jCookie.ClientCookieEncoder.LAX
        val builder = new jCookie.DefaultCookie(cookie.name, cookie.content)
        encoder.encode(builder)
      }
    }
  }

  implicit object ResponseCookieEncoder extends CookieEncoder[Response] {
    override final val unsafe: UnsafeAPI = new UnsafeAPI {
      override final def encode(cookie: Cookie[Response], validate: Boolean)(implicit unsafe: Unsafe): String = {
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
}
