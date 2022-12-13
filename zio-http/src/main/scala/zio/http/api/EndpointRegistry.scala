package zio.http.api

import zio.http.URL
import zio.stacktracer.TracingImplicits.disableAutoTrace // scalafix:ok;

final case class EndpointRegistry[-MI, +MO, +Ids] private (private val map: Map[EndpointSpec[_, _, _], URL])
    extends EndpointLocator { self =>
  def locate[A, E, B](api: EndpointSpec[A, E, B]): Option[URL] = map.get(api)
}
object EndpointRegistry     {
  def apply[MI, MO, Ids](address: URL, spec: ServiceSpec[MI, MO, Ids]): EndpointRegistry[MI, MO, Ids] = {
    val map = spec.apis
      .foldLeft[Map[EndpointSpec[_, _, _], URL]](Map.empty) { case (map, api) =>
        map.updated(api, address)
      }

    new EndpointRegistry[MI, MO, Ids](map)
  }
}
