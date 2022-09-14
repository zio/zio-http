package zio.http
package netty

import io.netty.channel._
import io.netty.channel.epoll.Epoll
import io.netty.channel.kqueue.KQueue
import io.netty.incubator.channel.uring.IOUringEventLoopGroup
import zio._

import java.util.concurrent.Executor
import io.netty.channel.nio.NioEventLoopGroup
import io.netty.channel.epoll.EpollEventLoopGroup
import io.netty.channel.kqueue.KQueueEventLoopGroup

/**
 * Simple wrapper over NioEventLoopGroup
 */
object EventLoopGroups {
  def nio(nThreads: Int = 0): ZLayer[Any, Nothing, EventLoopGroup] = ZLayer.scoped(EventLoopGroups.Live.nio(nThreads))

  def kQueue(nThreads: Int = 0): ZLayer[Any, Nothing, EventLoopGroup] =
    ZLayer.scoped(EventLoopGroups.Live.kQueue(nThreads))

  def epoll(nThreads: Int = 0): ZLayer[Any, Nothing, EventLoopGroup] =
    ZLayer.scoped(EventLoopGroups.Live.epoll(nThreads))

  def uring(nThreads: Int = 0): ZLayer[Any, Nothing, EventLoopGroup] =
    ZLayer.scoped(EventLoopGroups.Live.uring(nThreads))

  def auto(nThreads: Int = 0): ZLayer[Any, Nothing, EventLoopGroup] = ZLayer.scoped(EventLoopGroups.Live.auto(nThreads))

  def default: ZLayer[Any, Nothing, EventLoopGroup] = ZLayer.scoped(EventLoopGroups.Live.default)

  object Live {
    def nio(nThreads: Int): ZIO[Scope, Nothing, EventLoopGroup] =
      make(ZIO.succeed(new NioEventLoopGroup(nThreads)))

    def nio(nThreads: Int, executor: Executor): ZIO[Scope, Nothing, EventLoopGroup] =
      make(ZIO.succeed(new NioEventLoopGroup(nThreads, executor)))

    def make(eventLoopGroup: UIO[EventLoopGroup]): ZIO[Scope, Nothing, EventLoopGroup] =
      ZIO.acquireRelease(eventLoopGroup)(ev => NettyFutureExecutor.unit(ev.shutdownGracefully).orDie)

    def epoll(nThreads: Int): ZIO[Scope, Nothing, EventLoopGroup] =
      make(ZIO.succeed(new EpollEventLoopGroup(nThreads)))

    def kQueue(nThreads: Int): ZIO[Scope, Nothing, EventLoopGroup] =
      make(ZIO.succeed(new KQueueEventLoopGroup(nThreads)))

    def epoll(nThreads: Int, executor: Executor): ZIO[Scope, Nothing, EventLoopGroup] =
      make(ZIO.succeed(new EpollEventLoopGroup(nThreads, executor)))

    def uring(nThread: Int): ZIO[Scope, Nothing, EventLoopGroup] =
      make(ZIO.succeed(new IOUringEventLoopGroup(nThread)))

    def uring(nThread: Int, executor: Executor): ZIO[Scope, Nothing, EventLoopGroup] =
      make(ZIO.succeed(new IOUringEventLoopGroup(nThread, executor)))

    def auto(nThreads: Int): ZIO[Scope, Nothing, EventLoopGroup] =
      if (Epoll.isAvailable)
        epoll(nThreads)
      else if (KQueue.isAvailable)
        kQueue(nThreads)
      else nio(nThreads)

    def default: ZIO[Scope, Nothing, EventLoopGroup] = make(ZIO.succeed(new DefaultEventLoopGroup()))
  }

}
