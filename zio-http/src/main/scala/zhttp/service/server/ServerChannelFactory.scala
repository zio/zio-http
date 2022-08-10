package zhttp.service.server

import io.netty.channel.epoll.{Epoll, EpollServerSocketChannel}
import io.netty.channel.kqueue.{KQueue, KQueueServerSocketChannel}
import io.netty.channel.socket.nio.NioServerSocketChannel
import io.netty.channel.{ChannelFactory => JChannelFactory, ServerChannel}
import io.netty.incubator.channel.uring.IOUringServerSocketChannel
import zhttp.service.ChannelFactory
import zhttp.service.ChannelModel.ChannelType
import zio.UIO

private[zhttp] object ServerChannelFactory {

  def get(serverChannelType: ChannelType): UIO[JChannelFactory[ServerChannel]] = serverChannelType match {
    case ChannelType.NIO    => nio
    case ChannelType.EPOLL  => epoll
    case ChannelType.URING  => uring
    case ChannelType.KQUEUE => kQueue
    case ChannelType.AUTO   => auto
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
