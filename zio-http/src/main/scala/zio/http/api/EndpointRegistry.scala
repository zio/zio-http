package zio.http.api

import zio.http.URL
import zio.stacktracer.TracingImplicits.disableAutoTrace // scalafix:ok;

final case class EndpointRegistry[+Ids, -MI, +ME] private (private val map: Map[EndpointSpec[_, _, _], URL])
    extends EndpointLocator { self =>
  def locate[A, E, B](api: EndpointSpec[A, E, B]): Option[URL] = map.get(api)
}
object EndpointRegistry     {
  def apply[Ids, MI, ME](address: URL, spec: ServiceSpec[Ids, MI, ME, _]): EndpointRegistry[Ids, MI, ME] = {
    val map = spec.apis
      .foldLeft[Map[EndpointSpec[_, _, _], URL]](Map.empty) { case (map, api) =>
        map.updated(api, address)
      }

    new EndpointRegistry[Ids, MI, ME](map)
  }
}
