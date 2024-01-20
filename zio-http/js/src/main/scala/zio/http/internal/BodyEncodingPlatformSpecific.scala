package zio.http.internal

trait BodyEncodingPlatformSpecific {
  val default: BodyEncoding = throw new NotImplementedError("No version implemented for Scala.js yet.")
}
