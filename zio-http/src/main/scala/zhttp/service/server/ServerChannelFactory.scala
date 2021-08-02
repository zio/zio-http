package zhttp.service.server

import io.netty.channel.epoll.{Epoll, EpollServerSocketChannel}
import io.netty.channel.kqueue.{KQueue, KQueueServerSocketChannel}
import io.netty.channel.socket.nio.NioServerSocketChannel
import io.netty.channel.{ChannelFactory => JChannelFactory, ServerChannel}
import zhttp.service.{ChannelFactory, ServerChannelFactory}
import zio.{UIO, ZLayer}

object ServerChannelFactory {
  def nio: ZLayer[Any, Nothing, ServerChannelFactory]   = Live.nio.toLayer
  def epoll: ZLayer[Any, Nothing, ServerChannelFactory] = Live.epoll.toLayer
  def auto: ZLayer[Any, Nothing, ServerChannelFactory]  = Live.auto.toLayer

  object Live {
    def nio: UIO[JChannelFactory[ServerChannel]]    = ChannelFactory.make(() => new NioServerSocketChannel())
    def epoll: UIO[JChannelFactory[ServerChannel]]  = ChannelFactory.make(() => new EpollServerSocketChannel())
    def kQueue: UIO[JChannelFactory[ServerChannel]] = ChannelFactory.make(() => new KQueueServerSocketChannel())
    def auto: UIO[JChannelFactory[ServerChannel]]   =
      if (Epoll.isAvailable) epoll
      else if (KQueue.isAvailable) kQueue
      else nio
  }
}
