package zio.http.api

import zio.http.URL
import zio.stacktracer.TracingImplicits.disableAutoTrace // scalafix:ok;

trait EndpointLocator {
  def locate(api: EndpointSpec[_, _]): Option[URL]
}
