package zio.http.api

import zio.http.URL

trait EndpointLocator {
  def locate(api: EndpointSpec[_, _]): Option[URL]
}
