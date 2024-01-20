package zio.http.internal

import zio.http.netty.NettyCookieEncoding

private[http] trait CookieEncodingPlatformSpecific {
  val default: CookieEncoding = NettyCookieEncoding
}
