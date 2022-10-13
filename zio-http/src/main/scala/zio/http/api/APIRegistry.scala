package zio.http.api

import zio.http.URL
import zio.stacktracer.TracingImplicits.disableAutoTrace // scalafix:ok;

final case class APIRegistry[-MI, +MO, +Ids] private (private val map: Map[API[_, _], URL]) extends APILocator { self =>
  def locate(api: API[_, _]): Option[URL] = map.get(api)
}
object APIRegistry {
  def apply[MI, MO, Ids](address: URL, spec: ServiceSpec[MI, MO, Ids]): APIRegistry[MI, MO, Ids] = {
    val map = spec.apis
      .foldLeft[Map[API[_, _], URL]](Map.empty) { case (map, api) =>
        map.updated(api, address)
      }

    new APIRegistry[MI, MO, Ids](map)
  }
}
