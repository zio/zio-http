package zhttp.service

import io.netty.channel
import io.netty.channel.epoll.Epoll
import io.netty.channel.kqueue.KQueue
import zio._

import java.util.concurrent.Executor

/**
 * Simple wrapper over NioEventLoopGroup
 */
object EventLoopGroup {
  def nio(nThreads: Int): ZLayer[Any, Nothing, EventLoopGroup] = EventLoopGroup.Live.nio(nThreads).toLayer

  def epoll(nThreads: Int): ZLayer[Any, Nothing, EventLoopGroup] = EventLoopGroup.Live.epoll(nThreads).toLayer

  def auto(nThreads: Int = 0): ZLayer[Any, Nothing, EventLoopGroup] = EventLoopGroup.Live.auto(nThreads).toLayer

  def default: ZLayer[Any, Nothing, EventLoopGroup] = EventLoopGroup.Live.default.toLayer

  object Live {
    def nio(nThreads: Int): ZManaged[Any, Nothing, channel.EventLoopGroup] =
      make(UIO(new channel.nio.NioEventLoopGroup(nThreads)))

    def nio(nThreads: Int, executor: Executor): ZManaged[Any, Nothing, channel.EventLoopGroup] =
      make(UIO(new channel.nio.NioEventLoopGroup(nThreads, executor)))

    def make(eventLoopGroup: UIO[channel.EventLoopGroup]): ZManaged[Any, Nothing, channel.EventLoopGroup] =
      eventLoopGroup.toManaged(ev => ChannelFuture.unit(ev.shutdownGracefully).orDie)

    def epoll(nThreads: Int): ZManaged[Any, Nothing, channel.EventLoopGroup] =
      make(UIO(new channel.epoll.EpollEventLoopGroup(nThreads)))

    def kQueue(nThreads: Int): ZManaged[Any, Nothing, channel.EventLoopGroup] =
      make(UIO(new channel.kqueue.KQueueEventLoopGroup(nThreads)))

    def epoll(nThreads: Int, executor: Executor): ZManaged[Any, Nothing, channel.EventLoopGroup] =
      make(UIO(new channel.epoll.EpollEventLoopGroup(nThreads, executor)))

    def auto(nThreads: Int): ZManaged[Any, Nothing, channel.EventLoopGroup] =
      if (Epoll.isAvailable)
        epoll(nThreads)
      else if (KQueue.isAvailable)
        kQueue(nThreads)
      else nio(nThreads)

    def default: ZManaged[Any, Nothing, channel.EventLoopGroup] = make(UIO(new channel.DefaultEventLoopGroup()))
  }

}
