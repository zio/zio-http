package zhttp.http

import io.netty.handler.codec.http.HttpHeaderNames

/**
 * Checks if the type has a way to extract cookie
 */
sealed trait HasCookie[-A] {
  def headers(a: A): List[String]
  def decode(a: A): List[Cookie]
}

object HasCookie {
  implicit object RequestCookie extends HasCookie[Request] {
    override def headers(a: Request): List[String] =
      a.getHeaderValues(HttpHeaderNames.COOKIE)

    override def decode(a: Request): List[Cookie] =
      headers(a).flatMap { header =>
        Cookie.decodeMultiple(header) match {
          case Left(_)     => Nil
          case Right(list) => list
        }
      }
  }

  implicit object ResponseCookie extends HasCookie[Response[Any, Nothing]] {
    override def headers(a: Response[Any, Nothing]): List[String] =
      a.getHeaderValues(HttpHeaderNames.SET_COOKIE)

    override def decode(a: Response[Any, Nothing]): List[Cookie] =
      headers(a).map(Cookie.decode).collect { case Right(cookie) => cookie }
  }
}
