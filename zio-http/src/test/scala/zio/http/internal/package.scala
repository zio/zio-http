package zio.http

import zio.http.service.{ChannelFactory, EventLoopGroup, ServerChannelFactory}

package object internal {
  type HttpEnv = EventLoopGroup with ChannelFactory with DynamicServer with ServerChannelFactory
}
