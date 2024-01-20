package zio.http.internal

private[http] trait DateEncodingPlatformSpecific {
  val default: DateEncoding = throw new NotImplementedError("No version implemented for Scala.js yet.")
}
