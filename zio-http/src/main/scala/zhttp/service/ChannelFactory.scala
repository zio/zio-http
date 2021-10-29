package zhttp.service

import io.netty.channel.embedded.EmbeddedChannel
import io.netty.channel.epoll.{Epoll, EpollSocketChannel}
import io.netty.channel.kqueue.{KQueue, KQueueSocketChannel}
import io.netty.channel.socket.nio.NioSocketChannel
import io.netty.channel.{Channel, ChannelFactory => JChannelFactory}
import io.netty.incubator.channel.uring.IOUringSocketChannel
import zio.{Task, ZLayer}

object ChannelFactory {
  def nio: ZLayer[Any, Throwable, ChannelFactory]      = Live.nio.toLayer
  def epoll: ZLayer[Any, Throwable, ChannelFactory]    = Live.epoll.toLayer
  def uring: ZLayer[Any, Throwable, ChannelFactory]    = Live.uring.toLayer
  def embedded: ZLayer[Any, Throwable, ChannelFactory] = Live.embedded.toLayer
  def auto: ZLayer[Any, Throwable, ChannelFactory]     = Live.auto.toLayer

  def make[A <: Channel](fn: () => A): Task[JChannelFactory[A]] = Task(new JChannelFactory[A] {
    override def newChannel(): A = fn()
  })

  object Live {
    def nio: Task[JChannelFactory[Channel]]      = make(() => new NioSocketChannel())
    def epoll: Task[JChannelFactory[Channel]]    = make(() => new EpollSocketChannel())
    def kQueue: Task[JChannelFactory[Channel]]   = make(() => new KQueueSocketChannel())
    def uring: Task[JChannelFactory[Channel]]    = make(() => new IOUringSocketChannel())
    def embedded: Task[JChannelFactory[Channel]] = make(() => new EmbeddedChannel(false, false))
    def auto: Task[JChannelFactory[Channel]]     =
      if (Epoll.isAvailable) epoll
      else if (KQueue.isAvailable) kQueue
      else nio
  }

}