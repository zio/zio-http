package zio.http.internal

import scala.annotation.nowarn

@nowarn("msg=dead code")
private[http] trait CookieEncodingPlatformSpecific {
  val default: CookieEncoding = throw new NotImplementedError("No version implemented for Scala.js yet.")
}
