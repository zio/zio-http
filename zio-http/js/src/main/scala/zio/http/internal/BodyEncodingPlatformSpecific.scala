package zio.http.internal

import scala.annotation.nowarn

@nowarn("msg=dead code")
trait BodyEncodingPlatformSpecific {
  val default: BodyEncoding = throw new NotImplementedError("No version implemented for Scala.js yet.")
}
