package zio.http.api

import zio.http.URL
import zio.stacktracer.TracingImplicits.disableAutoTrace // scalafix:ok;

trait EndpointLocator {
  def locate[A, E, B](api: EndpointSpec[A, E, B]): Option[URL]
}
