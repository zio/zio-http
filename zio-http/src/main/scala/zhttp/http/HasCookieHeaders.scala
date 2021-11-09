package zhttp.http

import io.netty.handler.codec.http.HttpHeaderNames
import zhttp.service.Client.ClientParams

/**
 * Checks if the type has a way to extract cookie details
 */
sealed trait HasCookieHeaders[-A] {
  def apply(a: A): List[CharSequence]
}

object HasCookieHeaders {
  implicit object RequestCookie extends HasCookieHeaders[Request] {
    override def apply(a: Request): List[CharSequence] =
      a.getHeaderValues(HttpHeaderNames.COOKIE)
  }

  implicit object ResponseCookie extends HasCookieHeaders[Response[Any, Nothing]] {
    override def apply(a: Response[Any, Nothing]): List[CharSequence] =
      a.getHeaderValues(HttpHeaderNames.SET_COOKIE)
  }

  implicit object ClientParams extends HasCookieHeaders[ClientParams] {
    override def apply(a: ClientParams): List[CharSequence] = a.getHeaderValues(HttpHeaderNames.COOKIE)
  }
}
