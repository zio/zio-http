package zio.http.model.headers.values

import zio.http.CookieEncoder._
import zio.http.{CookieDecoder, Request, model}

sealed trait RequestCookie

/**
 * The Cookie HTTP request header contains stored HTTP cookies associated with
 * the server.
 */
object RequestCookie {

  final case class CookieValue(value: List[model.Cookie[Request]]) extends RequestCookie
  final case class InvalidCookieValue(error: Exception)            extends RequestCookie

  def toCookie(value: String): zio.http.model.headers.values.RequestCookie = {
    implicit val decoder = CookieDecoder.RequestCookieDecoder
    model.Cookie.decode(value) match {
      case Left(value)  => InvalidCookieValue(value)
      case Right(value) =>
        if (value.isEmpty) InvalidCookieValue(new Exception("invalid cookie"))
        else
          CookieValue(value)
    }
  }

  def fromCookie(cookie: RequestCookie): String = cookie match {
    case CookieValue(value)    =>
      value.map(_.encode.getOrElse("")).mkString("; ")
    case InvalidCookieValue(_) => ""
  }
}
