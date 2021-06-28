package zhttp.http

import io.netty.handler.codec.http.{HttpHeaderNames => JHttpHeaderNames}

private[zhttp] trait CookieHelpers { self: HasHeaders =>

  def cookies(): List[Cookie[Nothing]] =
    self.headers
      .filter(x => x.name.toString.equalsIgnoreCase(JHttpHeaderNames.SET_COOKIE.toString))
      .map(Cookie.toCookie)

  def cookieHeader(): List[Header] =
    self.headers.filter(x => x.name.toString.equalsIgnoreCase(JHttpHeaderNames.SET_COOKIE.toString))
}
