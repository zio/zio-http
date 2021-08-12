package zhttp.http

import io.netty.handler.codec.http.HttpHeaderNames

private[zhttp] trait CookieHelpers { self: HasHeaders =>

  def cookies: List[Cookie] = {
    self.headers
      .filter(x => x.name.toString.equalsIgnoreCase(HttpHeaderNames.SET_COOKIE.toString))
      .map(h => Cookie.fromString(h.value.toString))
  }

}
