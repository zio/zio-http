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
  def nio(nThreads: Int): ZLayer[Any, Nothing, EventLoopGroup] = EventLoopGroup.Live.nio(nThreads).toLayer.orDie

  def epoll(nThreads: Int): ZLayer[Any, Nothing, EventLoopGroup] = EventLoopGroup.Live.epoll(nThreads).toLayer.orDie

  def uring(nThreads: Int = 0): ZLayer[Any, Nothing, EventLoopGroup] = EventLoopGroup.Live.uring(nThreads).toLayer.orDie

  def auto(nThreads: Int = 0): ZLayer[Any, Nothing, EventLoopGroup] = EventLoopGroup.Live.auto(nThreads).toLayer.orDie

  def default: ZLayer[Any, Nothing, EventLoopGroup] = EventLoopGroup.Live.default.toLayer.orDie

  object Live {
    def nio(nThreads: Int): ZManaged[Any, Throwable, channel.EventLoopGroup] =
      make(Task(new channel.nio.NioEventLoopGroup(nThreads)))

    def nio(nThreads: Int, executor: Executor): ZManaged[Any, Throwable, channel.EventLoopGroup] =
      make(Task(new channel.nio.NioEventLoopGroup(nThreads, executor)))

    def make(eventLoopGroup: Task[channel.EventLoopGroup]): ZManaged[Any, Throwable, channel.EventLoopGroup] =
      eventLoopGroup.toManaged(ev => ChannelFuture.unit(ev.shutdownGracefully).orDie)

    def epoll(nThreads: Int): ZManaged[Any, Throwable, channel.EventLoopGroup] =
      make(Task(new channel.epoll.EpollEventLoopGroup(nThreads)))

    def kQueue(nThreads: Int): ZManaged[Any, Throwable, channel.EventLoopGroup] =
      make(Task(new channel.kqueue.KQueueEventLoopGroup(nThreads)))

    def epoll(nThreads: Int, executor: Executor): ZManaged[Any, Throwable, channel.EventLoopGroup] =
      make(Task(new channel.epoll.EpollEventLoopGroup(nThreads, executor)))

    def uring(nThread: Int): ZManaged[Any, Throwable, channel.EventLoopGroup] =
      make(Task(new IOUringEventLoopGroup(nThread)))

    def uring(nThread: Int, executor: Executor): ZManaged[Any, Throwable, channel.EventLoopGroup] =
      make(Task(new IOUringEventLoopGroup(nThread, executor)))

    def auto(nThreads: Int): ZManaged[Any, Throwable, channel.EventLoopGroup] =
      if (Epoll.isAvailable)
        epoll(nThreads)
      else if (KQueue.isAvailable)
        kQueue(nThreads)
      else nio(nThreads)

    def default: ZManaged[Any, Throwable, channel.EventLoopGroup] = make(Task(new channel.DefaultEventLoopGroup()))
  }

}