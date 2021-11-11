package zhttp.service.server

import io.netty.channel
import io.netty.channel.epoll.{Epoll, EpollServerSocketChannel}
import io.netty.channel.kqueue.{KQueue, KQueueServerSocketChannel}
import io.netty.channel.socket.nio.NioServerSocketChannel
import io.netty.channel.{ChannelFactory => JChannelFactory, ServerChannel}
import io.netty.incubator.channel.uring.IOUringServerSocketChannel
import zhttp.service.{ChannelFactory, EventLoopGroup}
import zio.{Task, ZManaged}

/**
 * Support for various transport types.
 *   - NIO Transport - Works on any Platform / OS that has Java support Native
 *   - Epoll Transport - Works on Linux only
 *   - Native Kqueue Transport - Works on any BSD but mainly on MacOS.
 */
sealed trait Transport
object Transport        {
  case object Nio    extends Transport
  case object Epoll  extends Transport
  case object KQueue extends Transport
  case object URing  extends Transport
  case object Auto   extends Transport

  def eventLoopGroup(transport: Transport, threads: Int): ZManaged[Any, Nothing, channel.EventLoopGroup] =
    transport match {
      case Nio    => EventLoopGroup.Live.nio(threads)
      case Epoll  => EventLoopGroup.Live.epoll(threads)
      case KQueue => EventLoopGroup.Live.kQueue(threads)
      case URing  => EventLoopGroup.Live.uring(threads)
      case Auto   => EventLoopGroup.Live.auto(threads)
    }

  import zhttp.service.server.TransportFactory._
  def channelInitializer(transport: Transport): Task[JChannelFactory[ServerChannel]] =
    transport match {
      case Nio    => nio
      case Epoll  => epoll
      case KQueue => kQueue
      case URing  => uring
      case Auto   => auto
    }
}
object TransportFactory {
  def nio: Task[JChannelFactory[ServerChannel]]    = ChannelFactory.make(() => new NioServerSocketChannel())
  def epoll: Task[JChannelFactory[ServerChannel]]  = ChannelFactory.make(() => new EpollServerSocketChannel())
  def uring: Task[JChannelFactory[ServerChannel]]  = ChannelFactory.make(() => new IOUringServerSocketChannel())
  def kQueue: Task[JChannelFactory[ServerChannel]] = ChannelFactory.make(() => new KQueueServerSocketChannel())
  def auto: Task[JChannelFactory[ServerChannel]]   =
    if (Epoll.isAvailable) epoll
    else if (KQueue.isAvailable) kQueue
    else nio
}
