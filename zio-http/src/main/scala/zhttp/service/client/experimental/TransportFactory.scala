package zhttp.service.client.experimental

import io.netty.channel
import io.netty.channel.embedded.EmbeddedChannel
import io.netty.channel.epoll.{EpollServerSocketChannel, EpollSocketChannel}
import io.netty.channel.kqueue.{KQueueServerSocketChannel, KQueueSocketChannel}
import io.netty.channel.socket.nio.{NioServerSocketChannel, NioSocketChannel}
import io.netty.channel.{Channel, ServerChannel, kqueue, ChannelFactory => JChannelFactory}
import io.netty.incubator.channel.uring.{IOUringServerSocketChannel, IOUringSocketChannel}
import zhttp.service.{ChannelFactory, EventLoopGroup}
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
}
object Transport        {
  import TransportFactory._
  case object Nio    extends Transport {
    override def eventLoopGroup(nThreads: Int): ZManaged[Any, Nothing, channel.EventLoopGroup] =
      EventLoopGroup.Live.nio(nThreads)

    override def channelInitializer: Task[JChannelFactory[ServerChannel]] = nio

    override def clientChannel: Task[JChannelFactory[Channel]] = clientNio
  }
  case object Epoll  extends Transport {
    override def eventLoopGroup(nThreads: Int): ZManaged[Any, Nothing, channel.EventLoopGroup] =
      EventLoopGroup.Live.epoll(nThreads)

    override def channelInitializer: Task[JChannelFactory[ServerChannel]] = epoll

    override def clientChannel: Task[JChannelFactory[Channel]] = clientEpoll
  }
  case object KQueue extends Transport {
    override def eventLoopGroup(nThreads: Int): ZManaged[Any, Nothing, channel.EventLoopGroup] =
      EventLoopGroup.Live.kQueue(nThreads)

    override def channelInitializer: Task[JChannelFactory[ServerChannel]] = kQueue

    override def clientChannel: Task[JChannelFactory[Channel]] = clientKQueue
  }
  case object URing  extends Transport {
    override def eventLoopGroup(nThreads: Int): ZManaged[Any, Nothing, channel.EventLoopGroup] =
      EventLoopGroup.Live.uring(nThreads)

    override def channelInitializer: Task[JChannelFactory[ServerChannel]] = uring

    override def clientChannel: Task[JChannelFactory[Channel]] = clientUring
  }
  case object Auto   extends Transport {
    override def eventLoopGroup(nThreads: Int): ZManaged[Any, Nothing, channel.EventLoopGroup] =
      EventLoopGroup.Live.auto(nThreads)

    override def channelInitializer: Task[JChannelFactory[ServerChannel]] = auto

    override def clientChannel: Task[JChannelFactory[Channel]] = clientAuto
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