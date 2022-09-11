package zio.http.service

package object internal {
  type HttpEnv = EventLoopGroup with ChannelFactory with DynamicServer with ServerChannelFactory
}
