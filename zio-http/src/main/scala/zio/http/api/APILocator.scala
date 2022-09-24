package zio.http.api

import zio.http.URL

trait APILocator {
  def locate(api: API[_, _]): Option[URL]
}
