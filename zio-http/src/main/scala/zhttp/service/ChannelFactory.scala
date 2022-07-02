package zhttp.service

import io.netty.channel.embedded.EmbeddedChannel
import io.netty.channel.epoll.{Epoll, EpollSocketChannel}
import io.netty.channel.kqueue.{KQueue, KQueueSocketChannel}
import io.netty.channel.socket.nio.NioSocketChannel
import io.netty.channel.{Channel => JChannel, ChannelFactory => JChannelFactory}
import io.netty.incubator.channel.uring.IOUringSocketChannel
import zio.{UIO, ZLayer}

object ChannelFactory {
  def nio: ZLayer[Any, Nothing, ChannelFactory]      = Live.nio.toLayer
  def epoll: ZLayer[Any, Nothing, ChannelFactory]    = Live.epoll.toLayer
  def uring: ZLayer[Any, Nothing, ChannelFactory]    = Live.uring.toLayer
  def embedded: ZLayer[Any, Nothing, ChannelFactory] = Live.embedded.toLayer
  def auto: ZLayer[Any, Nothing, ChannelFactory]     = Live.auto.toLayer

  def make[A <: JChannel](fn: () => A): UIO[JChannelFactory[A]] = UIO(new JChannelFactory[A] {
    override def newChannel(): A = fn()
  })

  object Live {
    def nio: UIO[JChannelFactory[JChannel]]      = make(() => new NioSocketChannel())
    def epoll: UIO[JChannelFactory[JChannel]]    = make(() => new EpollSocketChannel())
    def kQueue: UIO[JChannelFactory[JChannel]]   = make(() => new KQueueSocketChannel())
    def uring: UIO[JChannelFactory[JChannel]]    = make(() => new IOUringSocketChannel())
    def embedded: UIO[JChannelFactory[JChannel]] = make(() => new EmbeddedChannel(false, false))
    def auto: UIO[JChannelFactory[JChannel]]     =
      if (Epoll.isAvailable) epoll
      else if (KQueue.isAvailable) kQueue
      else nio
  }

}
