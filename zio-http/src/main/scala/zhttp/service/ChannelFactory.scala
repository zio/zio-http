package zhttp.service

import io.netty.channel.embedded.EmbeddedChannel
import io.netty.channel.epoll.{Epoll, EpollSocketChannel}
import io.netty.channel.kqueue.{KQueue, KQueueSocketChannel}
import io.netty.channel.socket.nio.NioSocketChannel
import io.netty.channel.{Channel, ChannelFactory}
import zio.{UIO, ZLayer}

object HChannelFactory {
  def nio: ZLayer[Any, Nothing, HChannelFactory]      = Live.nio.toLayer
  def epoll: ZLayer[Any, Nothing, HChannelFactory]    = Live.epoll.toLayer
  def embedded: ZLayer[Any, Nothing, HChannelFactory] = Live.embedded.toLayer
  def auto: ZLayer[Any, Nothing, HChannelFactory]     = Live.auto.toLayer

  def make[A <: Channel](fn: () => A): UIO[ChannelFactory[A]] = UIO(new ChannelFactory[A] {
    override def newChannel(): A = fn()
  })

  object Live {
    def nio: UIO[ChannelFactory[Channel]]      = make(() => new NioSocketChannel())
    def epoll: UIO[ChannelFactory[Channel]]    = make(() => new EpollSocketChannel())
    def kQueue: UIO[ChannelFactory[Channel]]   = make(() => new KQueueSocketChannel())
    def embedded: UIO[ChannelFactory[Channel]] = make(() => new EmbeddedChannel(false, false))
    def auto: UIO[ChannelFactory[Channel]]     =
      if (Epoll.isAvailable) epoll
      else if (KQueue.isAvailable) kQueue
      else nio
  }

}
