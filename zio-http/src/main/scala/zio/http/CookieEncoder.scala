package zio.http

import zio.Unsafe

import zio.http.internal.CookieEncoding
import zio.http.model.Cookie

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
        CookieEncoding.default.encodeRequestCookie(cookie, validate)
      }
    }
  }

  implicit object ResponseCookieEncoder extends CookieEncoder[Response] {
    override final val unsafe: UnsafeAPI = new UnsafeAPI {
      override final def encode(cookie: Cookie[Response], validate: Boolean)(implicit unsafe: Unsafe): String = {
        CookieEncoding.default.encodeResponseCookie(cookie, validate)
      }
    }
  }
}
