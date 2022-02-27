package zhttp.service.client.transport

import io.netty.channel
import io.netty.channel.embedded.EmbeddedChannel
import io.netty.channel.epoll.{EpollEventLoopGroup, EpollServerSocketChannel, EpollSocketChannel}
import io.netty.channel.kqueue.{KQueueEventLoopGroup, KQueueServerSocketChannel, KQueueSocketChannel}
import io.netty.channel.socket.nio.{NioServerSocketChannel, NioSocketChannel}
import io.netty.channel.{Channel, ChannelFactory => JChannelFactory, ServerChannel, kqueue}
import io.netty.incubator.channel.uring.{IOUringEventLoopGroup, IOUringServerSocketChannel, IOUringSocketChannel}
import zhttp.service.ChannelFactory
import zio.Task

/**
 * Support for various transport types.
 *   - NIO Transport - Works on any Platform / OS that has Java support Native
 *   - Epoll Transport - Works on Linux only
 *   - Native Kqueue Transport - Works on any BSD but mainly on MacOS.
 *
 * A Factory for providing transport specific Channel / ServerChannel /
 * EventLoopGroup
 */
sealed trait Transport  {
  def serverChannel: Task[JChannelFactory[ServerChannel]]
  def clientChannel: Task[JChannelFactory[Channel]]
  def eventLoopGroup(nThreads: Int): Task[channel.EventLoopGroup]
}
object Transport        {
  import TransportFactory._
  case object Nio extends Transport {

    override def serverChannel: Task[JChannelFactory[ServerChannel]] = nio

    override def clientChannel: Task[JChannelFactory[Channel]] = clientNio

    override def eventLoopGroup(nThreads: Int): Task[channel.EventLoopGroup] =
      Task(new channel.nio.NioEventLoopGroup(nThreads))
  }
  case object Epoll  extends Transport {
    def isAvailable = io.netty.channel.epoll.Epoll.isAvailable

    override def serverChannel: Task[JChannelFactory[ServerChannel]] = epoll

    override def clientChannel: Task[JChannelFactory[Channel]] = clientEpoll

    override def eventLoopGroup(nThreads: Int): Task[channel.EventLoopGroup] =
      Task(new EpollEventLoopGroup(nThreads))
  }
  case object KQueue extends Transport {
    def isAvailable = io.netty.channel.kqueue.KQueue.isAvailable

    override def serverChannel: Task[JChannelFactory[ServerChannel]] = kQueue

    override def clientChannel: Task[JChannelFactory[Channel]] = clientKQueue

    override def eventLoopGroup(nThreads: Int): Task[channel.EventLoopGroup] =
      Task(new KQueueEventLoopGroup(nThreads))
  }
  case object URing  extends Transport {

    override def serverChannel: Task[JChannelFactory[ServerChannel]] = uring

    override def clientChannel: Task[JChannelFactory[Channel]] = clientUring

    override def eventLoopGroup(nThreads: Int): Task[channel.EventLoopGroup] =
      Task(new IOUringEventLoopGroup(nThreads))
  }
  case object Auto extends Transport {
    override def serverChannel: Task[JChannelFactory[ServerChannel]] = auto

    override def clientChannel: Task[JChannelFactory[Channel]] = clientAuto

    override def eventLoopGroup(nThreads: Int): Task[channel.EventLoopGroup] =
      if (Epoll.isAvailable) Epoll.eventLoopGroup(nThreads)
      else if (KQueue.isAvailable)
        KQueue.eventLoopGroup(nThreads)
      else Nio.eventLoopGroup(nThreads)

  }
}
object TransportFactory {
  def nio: Task[JChannelFactory[ServerChannel]]    = ChannelFactory.make(() => new NioServerSocketChannel())
  def epoll: Task[JChannelFactory[ServerChannel]]  = ChannelFactory.make(() => new EpollServerSocketChannel())
  def uring: Task[JChannelFactory[ServerChannel]]  = ChannelFactory.make(() => new IOUringServerSocketChannel())
  def kQueue: Task[JChannelFactory[ServerChannel]] = ChannelFactory.make(() => new KQueueServerSocketChannel())
  def auto: Task[JChannelFactory[ServerChannel]]   =
    if (channel.epoll.Epoll.isAvailable) epoll
    else if (kqueue.KQueue.isAvailable) kQueue
    else nio

  def clientNio: Task[JChannelFactory[Channel]]      = ChannelFactory.make(() => new NioSocketChannel())
  def clientEpoll: Task[JChannelFactory[Channel]]    = ChannelFactory.make(() => new EpollSocketChannel())
  def clientKQueue: Task[JChannelFactory[Channel]]   = ChannelFactory.make(() => new KQueueSocketChannel())
  def clientUring: Task[JChannelFactory[Channel]]    = ChannelFactory.make(() => new IOUringSocketChannel())
  def clientEmbedded: Task[JChannelFactory[Channel]] = ChannelFactory.make(() => new EmbeddedChannel(false, false))
  def clientAuto: Task[JChannelFactory[Channel]]     =
    if (channel.epoll.Epoll.isAvailable) clientEpoll
    else if (channel.kqueue.KQueue.isAvailable) clientKQueue
    else clientNio

}
