package zhttp.service

import io.netty.channel
import io.netty.channel.epoll.Epoll
import io.netty.channel.kqueue.KQueue
import io.netty.incubator.channel.uring.IOUringEventLoopGroup
import zio._

import java.util.concurrent.Executor

/**
 * Simple wrapper over NioEventLoopGroup
 */
object EventLoopGroup {
  def nio(nThreads: Int = 0): ZLayer[Any, Nothing, EventLoopGroup] = EventLoopGroup.Live.nio(nThreads).toLayer

  def epoll(nThreads: Int = 0): ZLayer[Any, Nothing, EventLoopGroup] = EventLoopGroup.Live.epoll(nThreads).toLayer

  def uring(nThreads: Int = 0): ZLayer[Any, Nothing, EventLoopGroup] = EventLoopGroup.Live.uring(nThreads).toLayer

  def auto(nThreads: Int = 0): ZLayer[Any, Nothing, EventLoopGroup] = EventLoopGroup.Live.auto(nThreads).toLayer

  def default: ZLayer[Any, Nothing, EventLoopGroup] = EventLoopGroup.Live.default.toLayer

  object Live {
    def nio(nThreads: Int): ZManaged[Any, Nothing, channel.EventLoopGroup] =
      make(UIO(new channel.nio.NioEventLoopGroup(nThreads)))

    def nio(nThreads: Int, executor: Executor): ZManaged[Any, Nothing, channel.EventLoopGroup] =
      make(UIO(new channel.nio.NioEventLoopGroup(nThreads, executor)))

    def make(eventLoopGroup: UIO[channel.EventLoopGroup]): ZManaged[Any, Nothing, channel.EventLoopGroup] =
      eventLoopGroup.toManagedWith(ev => ChannelFuture.unit(ev.shutdownGracefully).orDie)

    def epoll(nThreads: Int): ZManaged[Any, Nothing, channel.EventLoopGroup] =
      make(UIO(new channel.epoll.EpollEventLoopGroup(nThreads)))

    def kQueue(nThreads: Int): ZManaged[Any, Nothing, channel.EventLoopGroup] =
      make(UIO(new channel.kqueue.KQueueEventLoopGroup(nThreads)))

    def epoll(nThreads: Int, executor: Executor): ZManaged[Any, Nothing, channel.EventLoopGroup] =
      make(UIO(new channel.epoll.EpollEventLoopGroup(nThreads, executor)))

    def uring(nThread: Int): ZManaged[Any, Nothing, channel.EventLoopGroup] =
      make(UIO(new IOUringEventLoopGroup(nThread)))

    def uring(nThread: Int, executor: Executor): ZManaged[Any, Nothing, channel.EventLoopGroup] =
      make(UIO(new IOUringEventLoopGroup(nThread, executor)))

    def auto(nThreads: Int): ZManaged[Any, Nothing, channel.EventLoopGroup] =
      if (Epoll.isAvailable)
        epoll(nThreads)
      else if (KQueue.isAvailable)
        kQueue(nThreads)
      else nio(nThreads)

    def default: ZManaged[Any, Nothing, channel.EventLoopGroup] = make(UIO(new channel.DefaultEventLoopGroup()))
  }

}
