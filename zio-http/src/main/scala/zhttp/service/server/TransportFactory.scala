package zhttp.service.server

import io.netty.channel.epoll.{Epoll, EpollServerSocketChannel}
import io.netty.channel.kqueue.{KQueue, KQueueServerSocketChannel}
import io.netty.channel.socket.nio.NioServerSocketChannel
import io.netty.channel.{ServerChannel, ChannelFactory => JChannelFactory}
import io.netty.incubator.channel.uring.IOUringServerSocketChannel
import zhttp.service.{ChannelFactory}
import zio.{UIO}

sealed trait Transport
object Transport {
    case object Nio extends Transport
    case object Epoll extends Transport
    case object KQueue extends Transport
    case object URing extends Transport
    case object Auto extends Transport

    import zhttp.service.server.TransportFactory._
    def make(trans: Transport) = trans match {
        case Nio    => nio
        case Epoll  => epoll
        case KQueue => kQueue
        case URing  => uring
        case Auto   => auto
    }
}
object TransportFactory {
    def nio: UIO[JChannelFactory[ServerChannel]]    = ChannelFactory.make(() => new NioServerSocketChannel())
    def epoll: UIO[JChannelFactory[ServerChannel]]  = ChannelFactory.make(() => new EpollServerSocketChannel())
    def uring: UIO[JChannelFactory[ServerChannel]]  = ChannelFactory.make(() => new IOUringServerSocketChannel())
    def kQueue: UIO[JChannelFactory[ServerChannel]] = ChannelFactory.make(() => new KQueueServerSocketChannel())
    def auto: UIO[JChannelFactory[ServerChannel]]   =
      if (Epoll.isAvailable) epoll
      else if (KQueue.isAvailable) kQueue
      else nio
}
