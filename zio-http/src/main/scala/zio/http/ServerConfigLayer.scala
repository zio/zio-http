package zio.http

import zio.{ULayer, ZLayer}
import zio.http.service.{EventLoopGroup, ServerChannelFactory}

object ServerConfigLayer {

  val default: ZLayer[Any, Nothing, ServerConfig with EventLoopGroup with ServerChannelFactory] = {
    val configLayer: ULayer[ServerConfig] = ZLayer.succeed(ServerConfig())
    configLayer ++ EventLoopGroup.auto(0) ++ ServerChannelFactory.auto
  }

  def live(config: ServerConfig): ZLayer[Any, Any, ServerConfig with EventLoopGroup with ServerChannelFactory] = {
    val (eventLoopGroupLayer, serverChannelFactoryLayer) = config.channelType match {
      case ChannelType.NIO => (EventLoopGroup.nio(config.nThreads), ServerChannelFactory.nio)
      case ChannelType.EPOLL => (EventLoopGroup.epoll(config.nThreads), ServerChannelFactory.epoll)
      case ChannelType.KQUEUE => (EventLoopGroup.kQueue(config.nThreads), ServerChannelFactory.kQueue)
      case ChannelType.URING => (EventLoopGroup.uring(config.nThreads), ServerChannelFactory.uring)
      case ChannelType.AUTO => (EventLoopGroup.auto(config.nThreads), ServerChannelFactory.auto)
    }
    val configLayer: ULayer[ServerConfig] = ZLayer.succeed(config)
    configLayer ++ eventLoopGroupLayer ++ serverChannelFactoryLayer
  }

  val testServerConfig: ZLayer[Any, Nothing, ServerConfig with EventLoopGroup with ServerChannelFactory] = {
    val configLayer: ULayer[ServerConfig] = ZLayer.succeed(ServerConfig().withPort(0).withLeakDetection(LeakDetectionLevel.PARANOID))
    configLayer ++ EventLoopGroup.nio(0) ++ ServerChannelFactory.nio
  }

}