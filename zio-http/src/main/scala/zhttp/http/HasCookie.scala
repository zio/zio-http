package zhttp.http

import io.netty.handler.codec.http.HttpHeaderNames

/**
 * Checks if the type has a way to extract cookie
 */
sealed trait HasCookie[-A] {
  def headers(a: A): List[String]
  def decode(a: A): List[Cookie]
  def decodeSignedCookie(a: A, secret: String): List[Cookie]
}

object HasCookie {
  implicit object RequestCookie extends HasCookie[Request] {
    override def headers(a: Request): List[String] =
      a.getHeaderValues(HttpHeaderNames.COOKIE)

    override def decode(a: Request): List[Cookie] =
      headers(a).flatMap { header =>
        Cookie.decodeRequestCookie(header) match {
          case None       => Nil
          case Some(list) => list
        }
      }

    override def decodeSignedCookie(a: Request, secret: String): List[Cookie] =
      headers(a).map(headerValue => Cookie.decodeResponseSignedCookie(headerValue, Some(secret))).collect {
        case Some(cookie) => cookie
      }

  }

  implicit object ResponseCookie extends HasCookie[Response[Any, Nothing]] {
    override def headers(a: Response[Any, Nothing]): List[String] =
      a.getHeaderValues(HttpHeaderNames.SET_COOKIE)

    override def decode(a: Response[Any, Nothing]): List[Cookie] =
      headers(a).map(Cookie.decodeResponseCookie).collect { case Some(cookie) => cookie }

    override def decodeSignedCookie(a: Response[Any, Nothing], secret: String): List[Cookie] =
      headers(a).map(headerValue => Cookie.decodeResponseSignedCookie(headerValue, Some(secret))).collect {
        case Some(cookie) => cookie
      }
  }
}
