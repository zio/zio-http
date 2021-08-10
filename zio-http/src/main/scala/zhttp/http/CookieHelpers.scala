package zhttp.http

import io.netty.handler.codec.http.{HttpHeaderNames => JHttpHeaderNames}

private[zhttp] trait CookieHelpers { self: HasHeaders =>

  def cookies: List[Cookie[Meta]] = {
    self.headers
      .filter(x => x.name.toString.equalsIgnoreCase(JHttpHeaderNames.SET_COOKIE.toString))
      .map(h => Cookie.toCookie(h.value.toString))
  }
}
