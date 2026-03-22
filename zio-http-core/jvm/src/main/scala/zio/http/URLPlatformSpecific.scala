package zio.http

import scala.util.Try

trait URLPlatformSpecific {
  self: URL =>

  /**
   * Returns a new java.net.URL only if this URL represents an absolute
   * location.
   */
  def toJavaURL: Option[java.net.URL] =
    if (self.kind == URL.Location.Relative) None else Try(toJavaURI.toURL).toOption

}
