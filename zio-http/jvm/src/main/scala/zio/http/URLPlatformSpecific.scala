package zio.http

import scala.util.Try

/**
 * Members of `URL` that exist only on the JVM: `toJavaURL`. On Scala.js this
 * trait has no members.
 */
trait URLPlatformSpecific {
  self: URL =>

  /**
   * Returns a new java.net.URL only if this URL represents an absolute
   * location. The result is also `None` when the JVM has no protocol handler
   * for the scheme, for example for `ws`. This member is available only on the
   * JVM.
   */
  def toJavaURL: Option[java.net.URL] =
    if (self.kind == URL.Location.Relative) None else Try(toJavaURI.toURL).toOption

}
