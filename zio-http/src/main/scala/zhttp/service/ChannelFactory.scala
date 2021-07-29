package zhttp.service

import io.netty.channel.embedded.EmbeddedChannel
import io.netty.channel.epoll.{Epoll => JEpoll, EpollSocketChannel}
import io.netty.channel.kqueue.{KQueue => JKQueue, KQueueSocketChannel}
import io.netty.channel.socket.nio.NioSocketChannel
import io.netty.channel.{Channel, ChannelFactory => HChannelFactory}
import zio.{UIO, ZLayer}

object ChannelFactory {
  def nio: ZLayer[Any, Nothing, ChannelFactory]      = Live.nio.toLayer
  def epoll: ZLayer[Any, Nothing, ChannelFactory]    = Live.epoll.toLayer
  def embedded: ZLayer[Any, Nothing, ChannelFactory] = Live.embedded.toLayer
  def auto: ZLayer[Any, Nothing, ChannelFactory]     = Live.auto.toLayer

  def make[A <: Channel](fn: () => A): UIO[HChannelFactory[A]] = UIO(new HChannelFactory[A] {
    override def newChannel(): A = fn()
  })

  object Live {
    def nio: UIO[HChannelFactory[Channel]]      = make(() => new NioSocketChannel())
    def epoll: UIO[HChannelFactory[Channel]]    = make(() => new EpollSocketChannel())
    def kQueue: UIO[HChannelFactory[Channel]]   = make(() => new KQueueSocketChannel())
    def embedded: UIO[HChannelFactory[Channel]] = make(() => new EmbeddedChannel(false, false))
    def auto: UIO[HChannelFactory[Channel]]     =
      if (JEpoll.isAvailable) epoll
      else if (JKQueue.isAvailable) kQueue
      else nio
  }

}
