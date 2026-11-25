package zio.http

import zio.Trace

/**
 * Platform-specific constructors of [[Handler]], mixed into its companion
 * object.
 */
trait HandlerPlatformSpecific {
  self: Handler.type =>

  /**
   * Creates a handler that serves the classpath resource at `path`, found with
   * the class loader of zio-http. A resource in a directory (a `file:` URL) is
   * served like `fromFile`, with `Range` support. A resource in a jar (a `jar:`
   * URL) is served without `Range` support: the response has
   * `Accept-Ranges: none`. The handler fails with a
   * `java.io.FileNotFoundException` when the resource does not exist.
   *
   * On Scala.js, this method has no `charset` parameter and throws an
   * `UnsupportedOperationException` when it is called.
   */
  def fromResource(path: String)(implicit trace: Trace): Handler[Any, Throwable, Any, Response] =
    throw new UnsupportedOperationException("Not supported on Scala.js")
}
