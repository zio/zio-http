package zio.http.api

import zio._
import zio.http.URL
import zio.stacktracer.TracingImplicits.disableAutoTrace // scalafix:ok;

/**
 * An endpoint locator is responsible for locating endpoints.
 */
trait EndpointLocator { self =>

  /**
   * Returns the location to the specified endpoint, or fails with an endpoint
   * error.
   */
  def locate[A, E, B, M <: EndpointMiddleware](api: Endpoint[A, E, B, M])(implicit trace: Trace): IO[EndpointError, URL]

  final def orElse(that: EndpointLocator): EndpointLocator = new EndpointLocator {
    def locate[A, E, B, M <: EndpointMiddleware](api: Endpoint[A, E, B, M])(implicit
      trace: Trace,
    ): IO[EndpointError, URL] =
      self.locate(api).orElse(that.locate(api))
  }
}
object EndpointLocator {
  def fromURL(url: URL): EndpointLocator = new EndpointLocator {
    val effect = ZIO.succeedNow(url)

    def locate[A, E, B, M <: EndpointMiddleware](api: Endpoint[A, E, B, M])(implicit
      trace: Trace,
    ): IO[EndpointError, URL] =
      effect
  }
}
