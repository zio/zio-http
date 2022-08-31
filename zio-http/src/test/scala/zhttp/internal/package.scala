package zhttp

import zhttp.service.ChannelModel.ChannelType
import zhttp.service.{Client, EventLoopGroup}
import zio.ZIO

package object internal {
  type HttpEnv = EventLoopGroup with DynamicServer

  val testClient: ZIO[EventLoopGroup, Nothing, Client[Any]]       = Client.make[Any](channelType = ChannelType.NIO)
  val websocketTestClient: ZIO[HttpEnv, Nothing, Client[HttpEnv]] =
    Client.make[HttpEnv](channelType = ChannelType.NIO)
}
