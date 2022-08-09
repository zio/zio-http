package zhttp

import zhttp.service.{ChannelFactory, EventLoopGroup}

package object internal {
  type HttpEnv = EventLoopGroup with ChannelFactory with DynamicServer
}
