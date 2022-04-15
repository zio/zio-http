package zhttp.service

import io.netty.channel.embedded.EmbeddedChannel
import io.netty.channel.epoll.{Epoll, EpollSocketChannel}
import io.netty.channel.kqueue.{KQueue, KQueueSocketChannel}
import io.netty.channel.socket.nio.NioSocketChannel
import io.netty.channel.{Channel, ChannelFactory => JChannelFactory}
import io.netty.incubator.channel.uring.IOUringSocketChannel
import zio.{UIO, ZIO, ZLayer}

object ChannelFactory {
  def nio: ZLayer[Any, Nothing, ChannelFactory]      = ZLayer(Live.nio)
  def epoll: ZLayer[Any, Nothing, ChannelFactory]    = ZLayer(Live.epoll)
  def uring: ZLayer[Any, Nothing, ChannelFactory]    = ZLayer(Live.uring)
  def embedded: ZLayer[Any, Nothing, ChannelFactory] = ZLayer(Live.embedded)
  def auto: ZLayer[Any, Nothing, ChannelFactory]     = ZLayer(Live.auto)

  def make[A <: Channel](fn: () => A): UIO[JChannelFactory[A]] = ZIO.succeed(new JChannelFactory[A] {
    override def newChannel(): A = fn()
  })

  object Live {
    def nio: UIO[JChannelFactory[Channel]]      = make(() => new NioSocketChannel())
    def epoll: UIO[JChannelFactory[Channel]]    = make(() => new EpollSocketChannel())
    def kQueue: UIO[JChannelFactory[Channel]]   = make(() => new KQueueSocketChannel())
    def uring: UIO[JChannelFactory[Channel]]    = make(() => new IOUringSocketChannel())
    def embedded: UIO[JChannelFactory[Channel]] = make(() => new EmbeddedChannel(false, false))
    def auto: UIO[JChannelFactory[Channel]]     =
      if (Epoll.isAvailable) epoll
      else if (KQueue.isAvailable) kQueue
      else nio
  }

}
