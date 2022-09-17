package zio.http.api

trait APILocator {
  def locate(api: API[_, _]): Option[APIAddress]
}
