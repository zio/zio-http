package zio.http.api

import zio.http.URL
import zio.stacktracer.TracingImplicits.disableAutoTrace // scalafix:ok;

final case class APIRegistry[+Ids] private (private val map: Map[API[_, _], URL]) extends APILocator { self =>
  def locate(api: API[_, _]): Option[URL] = map.get(api)

  def register(api: API[_, _], address: URL): APIRegistry[Ids with api.Id] =
    copy(map = map + (api -> address)).withIds[Ids with api.Id]

  def registerAll[Ids2](address: URL)(apis: ServiceSpec[_, _, Ids2]): APIRegistry[Ids with Ids2] =
    apis.apis
      .foldLeft[APIRegistry[_]](self) { case (registry, api) =>
        registry.register(api, address)
      }
      .withIds[Ids with Ids2]

  private def withIds[Ids0]: APIRegistry[Ids0] =
    self.asInstanceOf[APIRegistry[Ids0]]
}
object APIRegistry {
  val empty: APIRegistry[Any] = new APIRegistry(Map.empty).withIds[Any]
}
