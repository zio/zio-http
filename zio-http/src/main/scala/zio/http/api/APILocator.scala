package zio.http.api

import zio.http.URL
import zio.stacktracer.TracingImplicits.disableAutoTrace // scalafix:ok;

trait APILocator {
  def locate(api: API[_, _]): Option[URL]
}
