package zhttp.http.cookie
import zhttp.http.cookie.Cookie.{Request, SameSite}
import io.netty.handler.codec.http.{cookie => jCookie}

sealed trait CookieEncoder[A] {
  final def apply(a: Cookie[A], strict: Boolean): String = encode(a, strict)
  def encode(a: Cookie[A], strict: Boolean): String
}

object CookieEncoder {

  implicit object RequestCookieEncoder extends CookieEncoder[Cookie.Request] {
    override def encode(cookie: Cookie[Request], strict: Boolean): String = {
      val encoder = if (strict) jCookie.ClientCookieEncoder.STRICT else jCookie.ClientCookieEncoder.LAX
      val builder = new jCookie.DefaultCookie(cookie.name, cookie.content)
      encoder.encode(builder)
    }
  }

  implicit object ResponseCookieEncoder extends CookieEncoder[Cookie.Response] {
    override def encode(cookie: Cookie[Cookie.Response], strict: Boolean): String = {
      val builder = new jCookie.DefaultCookie(cookie.name, cookie.content)

      val encoder = if (strict) jCookie.ServerCookieEncoder.STRICT else jCookie.ServerCookieEncoder.LAX

      cookie.domain.foreach(builder.setDomain)
      cookie.path.foreach(i => builder.setPath(i.encode))
      cookie.maxAge.foreach(i => builder.setMaxAge(i.toSeconds))
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
