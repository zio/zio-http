package zhttp.http

import io.netty.handler.codec.http.HttpHeaderNames

/**
 * Checks if the type has a way to extract cookie
 */
sealed trait HasCookie[-A] {
  def headers(a: A): List[String]
  def decode(a: A): List[Cookie]
  def unSign(a: A, secret: String): List[Cookie]
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

    override def unSign(a: Request, secret: String): List[Cookie] = {
      decode(a).map(cookie =>
        cookie.unSign(secret) match {
          case Some(value) => value
          case None        => cookie
        },
      )
    }
  }

  implicit object ResponseCookie extends HasCookie[Response[Any, Nothing]] {
    override def headers(a: Response[Any, Nothing]): List[String] =
      a.getHeaderValues(HttpHeaderNames.SET_COOKIE)

    override def decode(a: Response[Any, Nothing]): List[Cookie] =
      headers(a).map(Cookie.decodeResponseCookie).collect { case Some(cookie) => cookie }

    override def unSign(a: Response[Any, Nothing], secret: String): List[Cookie] =
      decode(a).map(cookie =>
        cookie.unSign(secret) match {
          case Some(value) => value
          case None        => cookie
        },
      )
  }
}
