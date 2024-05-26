package zio.http.internal

import scala.annotation.nowarn

@nowarn("msg=dead code")
private[http] trait DateEncodingPlatformSpecific {
  val default: DateEncoding = throw new NotImplementedError("No version implemented for Scala.js yet.")
}
