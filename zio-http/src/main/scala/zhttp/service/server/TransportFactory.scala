package zhttp.service.server

import io.netty.channel.epoll.{Epoll, EpollServerSocketChannel}
import io.netty.channel.kqueue.{KQueue, KQueueServerSocketChannel}
import io.netty.channel.socket.nio.NioServerSocketChannel
import io.netty.channel.{ServerChannel, ChannelFactory => JChannelFactory}
import io.netty.incubator.channel.uring.IOUringServerSocketChannel
import zhttp.service.{ChannelFactory, EventLoopGroup}
import zio.{Task, ZManaged}

sealed trait Transport
object Transport        {
  case object Nio    extends Transport
  case object Epoll  extends Transport
  case object KQueue extends Transport
  case object URing  extends Transport
  case object Auto   extends Transport

  import zhttp.service.server.TransportFactory._

//  case NioELG(nThreads)   => nio(nThreads)
//  case EpollELG(nThreads) => epoll(nThreads)
//  case UringELG(nThreads) => uring(nThreads)
//  case AutoELG(nThreads)  => auto(nThreads)
//  case DefaultELG         => default

  def make(trans: Transport) = trans match {
    case Nio    => ZManaged.fromEffect(nio).zip(EventLoopGroup.Live.nio(1))
    case Epoll  => ZManaged.fromEffect(epoll).zip(EventLoopGroup.Live.epoll(1))
    case KQueue => ZManaged.fromEffect(kQueue).zip(EventLoopGroup.Live.kQueue(1))
    case URing  => ZManaged.fromEffect(uring).zip(EventLoopGroup.Live.uring(1))
    case Auto   => ZManaged.fromEffect(auto).zip(EventLoopGroup.Live.auto(1))
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
