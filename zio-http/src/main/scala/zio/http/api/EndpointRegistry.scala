package zio.http.api

import zio.http.URL
import zio.stacktracer.TracingImplicits.disableAutoTrace // scalafix:ok;

final case class EndpointRegistry[-MI, +MO, +Ids] private (private val map: Map[EndpointSpec[_, _], URL])
    extends APILocator  { self =>
  def locate(api: EndpointSpec[_, _]): Option[URL] = map.get(api)
}
object EndpointRegistry {
  def apply[MI, MO, Ids](address: URL, spec: ServiceSpec[MI, MO, Ids]): EndpointRegistry[MI, MO, Ids] = {
    val map = spec.apis
      .foldLeft[Map[EndpointSpec[_, _], URL]](Map.empty) { case (map, api) =>
        map.updated(api, address)
      }

    new EndpointRegistry[MI, MO, Ids](map)
  }
}
