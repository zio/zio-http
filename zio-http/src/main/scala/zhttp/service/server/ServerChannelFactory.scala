package zhttp.service.server

import io.netty.channel.epoll.{Epoll, EpollServerSocketChannel}
import io.netty.channel.kqueue.{KQueue, KQueueServerSocketChannel}
import io.netty.channel.socket.nio.NioServerSocketChannel
import io.netty.channel.{ChannelFactory, ServerChannel}
import zhttp.service.{HChannelFactory, ServerChannelFactory}
import zio.{UIO, ZLayer}

object ServerChannelFactory {
  def nio: ZLayer[Any, Nothing, ServerChannelFactory]   = Live.nio.toLayer
  def epoll: ZLayer[Any, Nothing, ServerChannelFactory] = Live.epoll.toLayer
  def auto: ZLayer[Any, Nothing, ServerChannelFactory]  = Live.auto.toLayer

  object Live {
    def nio: UIO[ChannelFactory[ServerChannel]]    = HChannelFactory.make(() => new NioServerSocketChannel())
    def epoll: UIO[ChannelFactory[ServerChannel]]  = HChannelFactory.make(() => new EpollServerSocketChannel())
    def kQueue: UIO[ChannelFactory[ServerChannel]] = HChannelFactory.make(() => new KQueueServerSocketChannel())
    def auto: UIO[ChannelFactory[ServerChannel]]   =
      if (Epoll.isAvailable) epoll
      else if (KQueue.isAvailable) kQueue
      else nio
  }
}
