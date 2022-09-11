package zio.http.service

import io.netty.channel.epoll.{Epoll, EpollServerSocketChannel}
import io.netty.channel.kqueue.{KQueue, KQueueServerSocketChannel}
import io.netty.channel.socket.nio.NioServerSocketChannel
import io.netty.channel.{ChannelFactory => JChannelFactory, ServerChannel}
import io.netty.incubator.channel.uring.IOUringServerSocketChannel
import zio.{UIO, ZLayer}

object ServerChannelFactory {
  def nio: ZLayer[Any, Nothing, ServerChannelFactory]    = ZLayer(Live.nio)
  def epoll: ZLayer[Any, Nothing, ServerChannelFactory]  = ZLayer(Live.epoll)
  def uring: ZLayer[Any, Nothing, ServerChannelFactory]  = ZLayer(Live.uring)
  def auto: ZLayer[Any, Nothing, ServerChannelFactory]   = ZLayer(Live.auto)
  def kQueue: ZLayer[Any, Nothing, ServerChannelFactory] = ZLayer(Live.kQueue)

  object Live {
    def nio: UIO[JChannelFactory[ServerChannel]]    = ChannelFactory.make(() => new NioServerSocketChannel())
    def epoll: UIO[JChannelFactory[ServerChannel]]  = ChannelFactory.make(() => new EpollServerSocketChannel())
    def uring: UIO[JChannelFactory[ServerChannel]]  = ChannelFactory.make(() => new IOUringServerSocketChannel())
    def kQueue: UIO[JChannelFactory[ServerChannel]] = ChannelFactory.make(() => new KQueueServerSocketChannel())
    def auto: UIO[JChannelFactory[ServerChannel]]   =
      if (Epoll.isAvailable) epoll
      else if (KQueue.isAvailable) kQueue
      else nio
  }
}
