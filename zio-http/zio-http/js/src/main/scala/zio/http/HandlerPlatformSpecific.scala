package zio.http

import zio.Trace

trait HandlerPlatformSpecific {
  self: Handler.type =>

  def fromResource(path: String)(implicit trace: Trace): Handler[Any, Throwable, Any, Response] =
    throw new UnsupportedOperationException("Not supported on Scala.js")
}
