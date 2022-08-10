package zhttp.service

import io.netty.channel.embedded.EmbeddedChannel
import io.netty.channel.epoll.{Epoll, EpollSocketChannel}
import io.netty.channel.kqueue.{KQueue, KQueueSocketChannel}
import io.netty.channel.socket.nio.NioSocketChannel
import io.netty.channel.{Channel => JChannel, ChannelFactory => JChannelFactory}
import io.netty.incubator.channel.uring.IOUringSocketChannel
import zhttp.service.ChannelModel.ChannelType
import zio.{UIO, ZIO}

object ChannelFactory {

  def make[A <: JChannel](fn: () => A): UIO[JChannelFactory[A]] = ZIO.succeed(new JChannelFactory[A] {
    override def newChannel(): A = fn()
  })

  object Live {

    def get(channelType: ChannelType): UIO[JChannelFactory[JChannel]] = channelType match {
      case ChannelType.NIO    => nio
      case ChannelType.EPOLL  => epoll
      case ChannelType.URING  => uring
      case ChannelType.KQUEUE => kQueue
      case ChannelType.AUTO   => auto
    }
    def nio: UIO[JChannelFactory[JChannel]]                           = make(() => new NioSocketChannel())
    def epoll: UIO[JChannelFactory[JChannel]]                         = make(() => new EpollSocketChannel())
    def kQueue: UIO[JChannelFactory[JChannel]]                        = make(() => new KQueueSocketChannel())
    def uring: UIO[JChannelFactory[JChannel]]                         = make(() => new IOUringSocketChannel())
    def embedded: UIO[JChannelFactory[JChannel]]                      = make(() => new EmbeddedChannel(false, false))
    def auto: UIO[JChannelFactory[JChannel]]                          =
      if (Epoll.isAvailable) epoll
      else if (KQueue.isAvailable) kQueue
      else nio
  }

}
