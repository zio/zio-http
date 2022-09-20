package zio.http.api

import zio.http.URL
import zio.stacktracer.TracingImplicits.disableAutoTrace // scalafix:ok RemoveUnused.imports;

trait APILocator {
  def locate(api: API[_, _]): Option[URL]
}
