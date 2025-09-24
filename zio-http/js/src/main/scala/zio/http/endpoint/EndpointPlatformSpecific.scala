package zio.http.endpoint

import zio.http.URL

import org.scalajs.dom.window

trait EndpointPlatformSpecific[PathInput, Input, Err, Output, Auth <: AuthType] {
  self: Endpoint[PathInput, Input, Err, Output, Auth] =>

  /**
   * Creates an absolute URL for this endpoint, based on the current window
   * location.
   */
  def urlAbsolute(input: PathInput): Either[Exception, URL] =
    self.url(s"${window.location.protocol}//${window.location.host}", input)

}
