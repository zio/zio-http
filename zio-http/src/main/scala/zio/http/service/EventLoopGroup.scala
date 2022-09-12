package zio.http.service

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
  def nio(nThreads: Int = 0): ZLayer[Any, Nothing, EventLoopGroup] = ZLayer.scoped(EventLoopGroup.Live.nio(nThreads))

  def kQueue(nThreads: Int = 0): ZLayer[Any, Nothing, EventLoopGroup] =
    ZLayer.scoped(EventLoopGroup.Live.kQueue(nThreads))

  def epoll(nThreads: Int = 0): ZLayer[Any, Nothing, EventLoopGroup] =
    ZLayer.scoped(EventLoopGroup.Live.epoll(nThreads))

  def uring(nThreads: Int = 0): ZLayer[Any, Nothing, EventLoopGroup] =
    ZLayer.scoped(EventLoopGroup.Live.uring(nThreads))

  def auto(nThreads: Int = 0): ZLayer[Any, Nothing, EventLoopGroup] = ZLayer.scoped(EventLoopGroup.Live.auto(nThreads))

  def default: ZLayer[Any, Nothing, EventLoopGroup] = ZLayer.scoped(EventLoopGroup.Live.default)

  object Live {
    def nio(nThreads: Int): ZIO[Scope, Nothing, channel.EventLoopGroup] =
      make(ZIO.succeed(new channel.nio.NioEventLoopGroup(nThreads)))

    def nio(nThreads: Int, executor: Executor): ZIO[Scope, Nothing, channel.EventLoopGroup] =
      make(ZIO.succeed(new channel.nio.NioEventLoopGroup(nThreads, executor)))

    def make(eventLoopGroup: UIO[channel.EventLoopGroup]): ZIO[Scope, Nothing, channel.EventLoopGroup] =
      ZIO.acquireRelease(eventLoopGroup)(ev => ChannelFuture.unit(ev.shutdownGracefully).orDie)

    def epoll(nThreads: Int): ZIO[Scope, Nothing, channel.EventLoopGroup] =
      make(ZIO.succeed(new channel.epoll.EpollEventLoopGroup(nThreads)))

    def kQueue(nThreads: Int): ZIO[Scope, Nothing, channel.EventLoopGroup] =
      make(ZIO.succeed(new channel.kqueue.KQueueEventLoopGroup(nThreads)))

    def epoll(nThreads: Int, executor: Executor): ZIO[Scope, Nothing, channel.EventLoopGroup] =
      make(ZIO.succeed(new channel.epoll.EpollEventLoopGroup(nThreads, executor)))

    def uring(nThread: Int): ZIO[Scope, Nothing, channel.EventLoopGroup] =
      make(ZIO.succeed(new IOUringEventLoopGroup(nThread)))

    def uring(nThread: Int, executor: Executor): ZIO[Scope, Nothing, channel.EventLoopGroup] =
      make(ZIO.succeed(new IOUringEventLoopGroup(nThread, executor)))

    def auto(nThreads: Int): ZIO[Scope, Nothing, channel.EventLoopGroup] =
      if (Epoll.isAvailable)
        epoll(nThreads)
      else if (KQueue.isAvailable)
        kQueue(nThreads)
      else nio(nThreads)

    def default: ZIO[Scope, Nothing, channel.EventLoopGroup] = make(ZIO.succeed(new channel.DefaultEventLoopGroup()))
  }

}
