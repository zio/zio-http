package zio.http.api

final case class ServiceRegistry[Ids] private (private val map: Map[API[_, _], ServiceAddress]) { self =>
  def register(api: API[_, _], address: ServiceAddress): ServiceRegistry[Ids with api.Id] =
    copy(map = map + (api -> address)).withIds[Ids with api.Id]

  private def withIds[Ids0]: ServiceRegistry[Ids0] =
    self.asInstanceOf[ServiceRegistry[Ids0]]
}
object ServiceRegistry                                                                          {
  val empty: ServiceRegistry[Any] = new ServiceRegistry(Map.empty).withIds[Any]
}
