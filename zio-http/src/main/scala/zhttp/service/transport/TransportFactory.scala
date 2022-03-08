package zhttp.service.transport

import io.netty.channel
import io.netty.channel.embedded.EmbeddedChannel
import io.netty.channel.epoll.{EpollEventLoopGroup, EpollServerSocketChannel, EpollSocketChannel}
import io.netty.channel.kqueue.{KQueueEventLoopGroup, KQueueServerSocketChannel, KQueueSocketChannel}
import io.netty.channel.socket.nio.{NioServerSocketChannel, NioSocketChannel}
import io.netty.channel.{Channel, ChannelFactory => JChannelFactory, EventLoopGroup => JEventLoopGroup, ServerChannel, kqueue}
import io.netty.incubator.channel.uring.{IOUringEventLoopGroup, IOUringServerSocketChannel, IOUringSocketChannel}
import zhttp.service.transport.Transport._
import zio.{Has, Task, ZLayer}
sealed trait Transport { self =>
  def serverChannel: Task[JChannelFactory[ServerChannel]]
  def clientChannel: Task[JChannelFactory[Channel]]
  def eventLoopGroup(nThreads: Int = 0): Task[channel.EventLoopGroup]

  def eventLoopGroupLayer(nThreads: Int = 0): ZLayer[Any, Nothing, EventLoopGroup] = eventLoopGroup(
    nThreads,
  ).toLayer.orDie
  def clientLayer: ZLayer[Any, Nothing, ChannelFactory]                            = clientChannel.toLayer.orDie
}
object Transport       {

  /*
  Define Transport live layer for dependency injection usage (esp Specs)
   */
  type ChannelFactory = Has[JChannelFactory[Channel]]
  type EventLoopGroup = Has[JEventLoopGroup]

  import TransportFactory._
  case object Nio extends Transport { self =>

    override def serverChannel: Task[JChannelFactory[ServerChannel]] = nio

    override def clientChannel: Task[JChannelFactory[Channel]] = clientNio

    override def eventLoopGroup(nThreads: Int): Task[channel.EventLoopGroup] =
      Task(new channel.nio.NioEventLoopGroup(nThreads))
  }
  case object Epoll  extends Transport { self =>
    def isAvailable = io.netty.channel.epoll.Epoll.isAvailable

    override def serverChannel: Task[JChannelFactory[ServerChannel]] = epoll

    override def clientChannel: Task[JChannelFactory[Channel]] = clientEpoll

    override def eventLoopGroup(nThreads: Int): Task[channel.EventLoopGroup] =
      Task(new EpollEventLoopGroup(nThreads))
  }
  case object KQueue extends Transport { self =>
    def isAvailable = io.netty.channel.kqueue.KQueue.isAvailable

    override def serverChannel: Task[JChannelFactory[ServerChannel]] = kQueue

    override def clientChannel: Task[JChannelFactory[Channel]] = clientKQueue

    override def eventLoopGroup(nThreads: Int): Task[channel.EventLoopGroup] =
      Task(new KQueueEventLoopGroup(nThreads))
  }
  case object URing  extends Transport { self =>

    override def serverChannel: Task[JChannelFactory[ServerChannel]] = uring

    override def clientChannel: Task[JChannelFactory[Channel]] = clientUring

    override def eventLoopGroup(nThreads: Int): Task[channel.EventLoopGroup] =
      Task(new IOUringEventLoopGroup(nThreads))
  }
  case object Auto extends Transport { self =>
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

  def make[A <: Channel](fn: () => A): Task[JChannelFactory[A]] = Task(new JChannelFactory[A] {
    override def newChannel(): A = fn()
  })

  def nio: Task[JChannelFactory[ServerChannel]]    = make(() => new NioServerSocketChannel())
  def epoll: Task[JChannelFactory[ServerChannel]]  = make(() => new EpollServerSocketChannel())
  def uring: Task[JChannelFactory[ServerChannel]]  = make(() => new IOUringServerSocketChannel())
  def kQueue: Task[JChannelFactory[ServerChannel]] = make(() => new KQueueServerSocketChannel())
  def auto: Task[JChannelFactory[ServerChannel]]   =
    if (channel.epoll.Epoll.isAvailable) epoll
    else if (kqueue.KQueue.isAvailable) kQueue
    else nio

  def clientNio: Task[JChannelFactory[Channel]]      = make(() => new NioSocketChannel())
  def clientEpoll: Task[JChannelFactory[Channel]]    = make(() => new EpollSocketChannel())
  def clientKQueue: Task[JChannelFactory[Channel]]   = make(() => new KQueueSocketChannel())
  def clientUring: Task[JChannelFactory[Channel]]    = make(() => new IOUringSocketChannel())
  def clientEmbedded: Task[JChannelFactory[Channel]] = make(() => new EmbeddedChannel(false, false))
  def clientAuto: Task[JChannelFactory[Channel]]     =
    if (channel.epoll.Epoll.isAvailable) clientEpoll
    else if (channel.kqueue.KQueue.isAvailable) clientKQueue
    else clientNio

}
