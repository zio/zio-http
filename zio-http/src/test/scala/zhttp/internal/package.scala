package zhttp

import zhttp.service.ChannelModel.ChannelType
import zhttp.service.Client
import zio.{Scope, ZLayer}

package object internal {
  type HttpEnv = DynamicServer

  val testClientLayer: ZLayer[Scope, Nothing, Client[Any]]                       =
    ZLayer.fromZIO(Client.make[Any](channelType = ChannelType.NIO))
  val webSocketClientLayer: ZLayer[HttpEnv with Scope, Nothing, Client[HttpEnv]] =
    ZLayer.fromZIO(Client.make[HttpEnv](channelType = ChannelType.AUTO))

}
