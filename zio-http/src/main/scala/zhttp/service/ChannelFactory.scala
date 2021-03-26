package zhttp.service

import io.netty.channel.epoll.{Epoll => JEpoll}
import io.netty.channel.kqueue.{KQueue => JKQueue}
import zhttp.core._
import zio.{UIO, ZLayer}

object ChannelFactory {
  def nio: ZLayer[Any, Nothing, ChannelFactory]      = Live.nio.toLayer
  def epoll: ZLayer[Any, Nothing, ChannelFactory]    = Live.epoll.toLayer
  def embedded: ZLayer[Any, Nothing, ChannelFactory] = Live.embedded.toLayer
  def auto: ZLayer[Any, Nothing, ChannelFactory]     = Live.auto.toLayer

  def make[A <: JChannel](fn: () => A): UIO[JChannelFactory[A]] = UIO(new JChannelFactory[A] {
    override def newChannel(): A = fn()
  })

  object Live {
    def nio: UIO[JChannelFactory[JChannel]]      = make(() => new JNioSocketChannel())
    def epoll: UIO[JChannelFactory[JChannel]]    = make(() => new JEpollSocketChannel())
    def kQueue: UIO[JChannelFactory[JChannel]]   = make(() => new JKQueueSocketChannel())
    def embedded: UIO[JChannelFactory[JChannel]] = make(() => new JEmbeddedChannel(false, false))
    def auto: UIO[JChannelFactory[JChannel]]     =
      if (JEpoll.isAvailable) epoll
      else if (JKQueue.isAvailable) kQueue
      else nio
  }

}
