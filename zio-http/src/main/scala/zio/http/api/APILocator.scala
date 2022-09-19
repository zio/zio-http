package zio.http.api

import zio.http.URL
import zio.stacktracer.TracingImplicits.disableAutoTrace

trait APILocator {
  def locate(api: API[_, _]): Option[URL]
}
