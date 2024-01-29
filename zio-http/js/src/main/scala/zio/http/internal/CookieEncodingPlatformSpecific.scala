package zio.http.internal

private[http] trait CookieEncodingPlatformSpecific {
  val default: CookieEncoding = throw new NotImplementedError("No version implemented for Scala.js yet.")
}
