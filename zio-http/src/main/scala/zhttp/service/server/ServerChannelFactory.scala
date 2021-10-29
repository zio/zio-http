package zhttp.service.server

import io.netty.channel.epoll.{Epoll, EpollServerSocketChannel}
import io.netty.channel.kqueue.{KQueue, KQueueServerSocketChannel}
import io.netty.channel.socket.nio.NioServerSocketChannel
import io.netty.channel.{ChannelFactory => JChannelFactory, ServerChannel}
import io.netty.incubator.channel.uring.IOUringServerSocketChannel
import zhttp.service.{ChannelFactory, ServerChannelFactory}
import zio.{Task, ZLayer}

object ServerChannelFactory {
  def nio: ZLayer[Any, Throwable, ServerChannelFactory]   = Live.nio.toLayer
  def epoll: ZLayer[Any, Throwable, ServerChannelFactory] = Live.epoll.toLayer
  def uring: ZLayer[Any, Throwable, ServerChannelFactory] = Live.uring.toLayer
  def auto: ZLayer[Any, Throwable, ServerChannelFactory]  = Live.auto.toLayer

  object Live {
    def nio: Task[JChannelFactory[ServerChannel]]    = ChannelFactory.make(() => new NioServerSocketChannel())
    def epoll: Task[JChannelFactory[ServerChannel]]  = ChannelFactory.make(() => new EpollServerSocketChannel())
    def uring: Task[JChannelFactory[ServerChannel]]  = ChannelFactory.make(() => new IOUringServerSocketChannel())
    def kQueue: Task[JChannelFactory[ServerChannel]] = ChannelFactory.make(() => new KQueueServerSocketChannel())
    def auto: Task[JChannelFactory[ServerChannel]]   =
      if (Epoll.isAvailable) epoll
      else if (KQueue.isAvailable) kQueue
      else nio
  }
}