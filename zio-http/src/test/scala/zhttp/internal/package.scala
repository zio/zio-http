package zhttp

import zhttp.service.{ChannelFactory, EventLoopGroup, ServerChannelFactory}
import zio.Has

package object internal {
  type DynamicServer = Has[DynamicServer.Service]
  type HttpEnv       = EventLoopGroup with ChannelFactory with DynamicServer with ServerChannelFactory
}
