package zhttp

import zhttp.service.ChannelModel.ChannelType
import zhttp.service.Client
import zhttp.service.Client.Config
import zio.{Scope, ZLayer}

package object internal {
  type HttpEnv = DynamicServer

  val testClientLayer: ZLayer[Scope, Nothing, Client[Any]]                       =
    ZLayer.fromZIO(Client.make[Any](Config.empty.withChannelType(ChannelType.NIO)))
  val webSocketClientLayer: ZLayer[HttpEnv with Scope, Nothing, Client[HttpEnv]] =
    ZLayer.fromZIO(Client.make[HttpEnv](Config.empty.withChannelType(ChannelType.NIO)))

}
