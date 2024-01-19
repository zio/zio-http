package zio.http.internal

private[http] trait HeaderEncodingPlatformSpecific {
  val default: HeaderEncoding = throw new NotImplementedError("No version implemented for Scala.js yet.")
}
