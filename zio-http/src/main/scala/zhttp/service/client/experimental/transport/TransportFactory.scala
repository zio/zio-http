package zhttp.service.client.experimental.transport

import io.netty.channel
import io.netty.channel.embedded.EmbeddedChannel
import io.netty.channel.epoll.{EpollServerSocketChannel, EpollSocketChannel}
import io.netty.channel.kqueue.{KQueueServerSocketChannel, KQueueSocketChannel}
import io.netty.channel.socket.nio.{NioServerSocketChannel, NioSocketChannel}
import io.netty.channel.{Channel, ChannelFactory => JChannelFactory, ServerChannel, kqueue}
import io.netty.incubator.channel.uring.{IOUringServerSocketChannel, IOUringSocketChannel}
import zhttp.service.ChannelFactory
import zio.{Task, ZManaged}

/**
 * Support for various transport types.
 *   - NIO Transport - Works on any Platform / OS that has Java support Native
 *   - Epoll Transport - Works on Linux only
 *   - Native Kqueue Transport - Works on any BSD but mainly on MacOS.
 */
sealed trait Transport  {
  def eventLoopGroup(nThreads: Int): ZManaged[Any, Nothing, channel.EventLoopGroup]
  def channelInitializer: Task[JChannelFactory[ServerChannel]]
  def clientChannel: Task[JChannelFactory[Channel]]
  def eventLoopGroupTask(nThreads: Int): Task[channel.EventLoopGroup]
}
object Transport        {
  import TransportFactory._
  case object Nio    extends Transport {
    override def eventLoopGroup(nThreads: Int): ZManaged[Any, Nothing, channel.EventLoopGroup] =
      EventLoopGroupN.Live.nio(nThreads)

    override def channelInitializer: Task[JChannelFactory[ServerChannel]] = nio

    override def clientChannel: Task[JChannelFactory[Channel]] = clientNio

    override def eventLoopGroupTask(nThreads: Int): Task[channel.EventLoopGroup] =
      EventLoopGroupN.nioTask(nThreads)
  }
  case object Epoll  extends Transport {
    override def eventLoopGroup(nThreads: Int): ZManaged[Any, Nothing, channel.EventLoopGroup] =
      EventLoopGroupN.Live.epoll(nThreads)

    override def channelInitializer: Task[JChannelFactory[ServerChannel]] = epoll

    override def clientChannel: Task[JChannelFactory[Channel]] = clientEpoll

    override def eventLoopGroupTask(nThreads: Int): Task[channel.EventLoopGroup] = EventLoopGroupN.epollTask(nThreads)
  }
  case object KQueue extends Transport {
    override def eventLoopGroup(nThreads: Int): ZManaged[Any, Nothing, channel.EventLoopGroup] =
      EventLoopGroupN.Live.kQueue(nThreads)

    override def channelInitializer: Task[JChannelFactory[ServerChannel]] = kQueue

    override def clientChannel: Task[JChannelFactory[Channel]] = clientKQueue

    override def eventLoopGroupTask(nThreads: Int): Task[channel.EventLoopGroup] =
      EventLoopGroupN.Live.kQueueTask(nThreads)
  }
  case object URing  extends Transport {
    override def eventLoopGroup(nThreads: Int): ZManaged[Any, Nothing, channel.EventLoopGroup] =
      EventLoopGroupN.Live.uring(nThreads)

    override def channelInitializer: Task[JChannelFactory[ServerChannel]] = uring

    override def clientChannel: Task[JChannelFactory[Channel]] = clientUring

    override def eventLoopGroupTask(nThreads: Int): Task[channel.EventLoopGroup] = EventLoopGroupN.uringTask(nThreads)
  }
  case object Auto   extends Transport {
    override def eventLoopGroup(nThreads: Int): ZManaged[Any, Nothing, channel.EventLoopGroup] =
      EventLoopGroupN.Live.auto(nThreads)

    override def channelInitializer: Task[JChannelFactory[ServerChannel]] = auto

    override def clientChannel: Task[JChannelFactory[Channel]] = clientAuto

    override def eventLoopGroupTask(nThreads: Int): Task[channel.EventLoopGroup] = EventLoopGroupN.autoTask(nThreads)
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
