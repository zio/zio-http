package zio.http.internal

import scala.annotation.nowarn

@nowarn("msg=dead code")
private[http] trait HeaderEncodingPlatformSpecific {
  val default: HeaderEncoding = throw new NotImplementedError("No version implemented for Scala.js yet.")
}
