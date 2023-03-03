package zio.http

import zio.Unsafe

import zio.http.internal.CookieEncoding
import zio.http.model.Cookie

sealed trait CookieDecoder[A] {
  type Out

  final def apply(cookie: String)(implicit unsafe: Unsafe): Out =
    this.unsafe.decode(cookie, validate = false)

  trait UnsafeAPI {
    def decode(header: String, validate: Boolean)(implicit unsafe: Unsafe): Out
  }

  val unsafe: UnsafeAPI
}

object CookieDecoder {
  implicit object RequestCookieDecoder extends CookieDecoder[Request] {
    override type Out = List[Cookie[Request]]

    override final val unsafe: UnsafeAPI = new UnsafeAPI {
      override final def decode(header: String, validate: Boolean)(implicit unsafe: Unsafe): List[Cookie[Request]] = {
        CookieEncoding.default.decodeRequestCookie(header, validate)
      }
    }
  }

  implicit object ResponseCookieDecoder extends CookieDecoder[Response] {
    override type Out = Cookie[Response]

    override final val unsafe: UnsafeAPI = new UnsafeAPI {
      override final def decode(header: String, validate: Boolean)(implicit unsafe: Unsafe): Cookie[Response] = {
        CookieEncoding.default.decodeResponseCookie(header, validate)
      }
    }
  }
}
