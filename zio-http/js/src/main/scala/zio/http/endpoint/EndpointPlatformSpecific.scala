package zio.http.endpoint

import zio.http.URL

import org.scalajs.dom.window

/**
 * Platform-specific members of [[Endpoint]]. On the JVM it adds no members; on
 * Scala.js it adds `urlAbsolute`.
 */
trait EndpointPlatformSpecific[PathInput, Input, Err, Output, Auth <: AuthType] {
  self: Endpoint[PathInput, Input, Err, Output, Auth] =>

  /**
   * Returns the URL of this endpoint for `input`, using the protocol and host
   * of `window.location` as the base. Returns a `Left` when the path cannot be
   * formatted from `input` or the result is not a valid URL.
   */
  def urlAbsolute(input: PathInput): Either[Exception, URL] =
    self.url(s"${window.location.protocol}//${window.location.host}", input)

}
