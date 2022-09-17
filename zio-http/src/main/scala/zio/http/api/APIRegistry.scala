package zio.http.api

final case class APIRegistry[+Ids] private (private val map: Map[API[_, _], APIAddress]) extends APILocator { self =>
  def locate(api: API[_, _]): Option[APIAddress] = map.get(api)

  def register(api: API[_, _], address: APIAddress): APIRegistry[Ids with api.Id] =
    copy(map = map + (api -> address)).withIds[Ids with api.Id]

  def registerAll[Ids2](address: APIAddress)(apis: APIs[Ids2]): APIRegistry[Ids with Ids2] =
    apis.flatten
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
