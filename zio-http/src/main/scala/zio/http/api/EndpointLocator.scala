package zio.http.api

import zio._
import zio.http.URL
import zio.stacktracer.TracingImplicits.disableAutoTrace // scalafix:ok;

trait EndpointLocator { self =>
  def locate[A, E, B, M <: EndpointMiddleware](api: Endpoint[A, E, B, M])(implicit trace: Trace): IO[EndpointError, URL]

  final def orElse(that: EndpointLocator): EndpointLocator = new EndpointLocator {
    def locate[A, E, B, M <: EndpointMiddleware](api: Endpoint[A, E, B, M])(implicit trace: Trace): IO[EndpointError, URL] =
      self.locate(api).orElse(that.locate(api))
  }
}
object EndpointLocator {
  def fromURL(url: URL): EndpointLocator = new EndpointLocator {
    def locate[A, E, B, M <: EndpointMiddleware](api: Endpoint[A, E, B, M])(implicit trace: Trace): IO[EndpointError, URL] =
      ZIO.succeedNow(url)
  }
}
