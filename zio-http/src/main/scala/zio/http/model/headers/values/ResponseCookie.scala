package zio.http.model.headers.values

import zio.http.{CookieDecoder, Response, model}

sealed trait ResponseCookie

object ResponseCookie {
  final case class CookieValue(value: model.Cookie[Response]) extends ResponseCookie
  final case class InvalidCookieValue(error: Exception)       extends ResponseCookie

  def toCookie(value: String): zio.http.model.headers.values.ResponseCookie = {
    implicit val decoder = CookieDecoder.ResponseCookieDecoder
    model.Cookie.decode(value) match {
      case Left(value)  => InvalidCookieValue(value)
      case Right(value) => CookieValue(value)
    }
  }

  def fromCookie(cookie: ResponseCookie): String = cookie match {
    case CookieValue(value)    =>
      value.encode.getOrElse("")
    case InvalidCookieValue(_) => ""
  }
}
