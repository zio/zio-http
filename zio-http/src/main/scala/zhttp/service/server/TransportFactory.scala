package zhttp.service.server

import io.netty.channel
import io.netty.channel.epoll.EpollServerSocketChannel
import io.netty.channel.kqueue.KQueueServerSocketChannel
import io.netty.channel.socket.nio.NioServerSocketChannel
import io.netty.channel.{ChannelFactory => JChannelFactory, ServerChannel, kqueue}
import io.netty.incubator.channel.uring.IOUringServerSocketChannel
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
}
object Transport        {
  import zhttp.service.server.TransportFactory._
  case object Nio    extends Transport {
    override def eventLoopGroup(nThreads: Int): ZManaged[Any, Nothing, channel.EventLoopGroup] =
      EventLoopGroup.Live.nio(nThreads)

    override def channelInitializer: Task[JChannelFactory[ServerChannel]] = nio
  }
  case object Epoll  extends Transport {
    override def eventLoopGroup(nThreads: Int): ZManaged[Any, Nothing, channel.EventLoopGroup] =
      EventLoopGroup.Live.epoll(nThreads)

    override def channelInitializer: Task[JChannelFactory[ServerChannel]] = epoll
  }
  case object KQueue extends Transport {
    override def eventLoopGroup(nThreads: Int): ZManaged[Any, Nothing, channel.EventLoopGroup] =
      EventLoopGroup.Live.kQueue(nThreads)

    override def channelInitializer: Task[JChannelFactory[ServerChannel]] = kQueue
  }
  case object URing  extends Transport {
    override def eventLoopGroup(nThreads: Int): ZManaged[Any, Nothing, channel.EventLoopGroup] =
      EventLoopGroup.Live.uring(nThreads)

    override def channelInitializer: Task[JChannelFactory[ServerChannel]] = uring
  }
  case object Auto   extends Transport {
    override def eventLoopGroup(nThreads: Int): ZManaged[Any, Nothing, channel.EventLoopGroup] =
      EventLoopGroup.Live.auto(nThreads)

    override def channelInitializer: Task[JChannelFactory[ServerChannel]] = auto
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
}
