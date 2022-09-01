package zhttp

import zhttp.service.ChannelModel.ChannelType
import zhttp.service.Client
import zio.{Scope, ZIO}

package object internal {
  type HttpEnv = DynamicServer

  val testClient: ZIO[Scope, Nothing, Client[Any]] = Client.make[Any](channelType = ChannelType.NIO)
  val websocketTestClient: ZIO[HttpEnv with Scope, Nothing, Client[HttpEnv]] =
    Client.make[HttpEnv](channelType = ChannelType.NIO)
}
