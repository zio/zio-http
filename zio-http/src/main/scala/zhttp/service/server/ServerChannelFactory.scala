package zhttp.service.server

import io.netty.channel.epoll.{Epoll, EpollServerSocketChannel}
import io.netty.channel.kqueue.{KQueue, KQueueServerSocketChannel}
import io.netty.channel.socket.nio.NioServerSocketChannel
import io.netty.channel.{ChannelFactory => JChannelFactory, ServerChannel}
import io.netty.incubator.channel.uring.IOUringServerSocketChannel
import zhttp.service.ChannelFactory
import zio.UIO

private[zhttp] object ServerChannelFactory {

  sealed trait ServerChannelType
  object ServerChannelType {
    case object NIO    extends ServerChannelType
    case object EPOLL  extends ServerChannelType
    case object URING  extends ServerChannelType
    case object KQUEUE extends ServerChannelType
    case object AUTO   extends ServerChannelType

  }

  def get(serverChannelType: ServerChannelType): UIO[JChannelFactory[ServerChannel]] = serverChannelType match {
    case ServerChannelType.NIO    => nio
    case ServerChannelType.EPOLL  => epoll
    case ServerChannelType.URING  => uring
    case ServerChannelType.KQUEUE => kQueue
    case ServerChannelType.AUTO   => auto
  }

  def nio: UIO[JChannelFactory[ServerChannel]]    = ChannelFactory.make(() => new NioServerSocketChannel())
  def epoll: UIO[JChannelFactory[ServerChannel]]  = ChannelFactory.make(() => new EpollServerSocketChannel())
  def uring: UIO[JChannelFactory[ServerChannel]]  = ChannelFactory.make(() => new IOUringServerSocketChannel())
  def kQueue: UIO[JChannelFactory[ServerChannel]] = ChannelFactory.make(() => new KQueueServerSocketChannel())
  def auto: UIO[JChannelFactory[ServerChannel]]   =
    if (Epoll.isAvailable) epoll
    else if (KQueue.isAvailable) kQueue
    else nio
}
