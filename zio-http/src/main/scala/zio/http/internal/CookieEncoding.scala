package zio.http.internal

import zio.http.model.Cookie
import zio.http.netty.NettyCookieEncoding
import zio.http.{Request, Response}

private[http] trait CookieEncoding {
  def encodeRequestCookie(cookie: Cookie[Request], validate: Boolean): String
  def decodeRequestCookie(header: String, validate: Boolean): List[Cookie[Request]]

  def encodeResponseCookie(cookie: Cookie[Response], validate: Boolean): String
  def decodeResponseCookie(header: String, validate: Boolean): Cookie[Response]
}

private[http] object CookieEncoding {
  val default: CookieEncoding = NettyCookieEncoding
}
