package zhttp.service.client.experimental.transport

import io.netty.channel
import io.netty.channel.epoll.{Epoll, EpollEventLoopGroup}
import io.netty.channel.kqueue.{KQueue, KQueueEventLoopGroup}
import io.netty.incubator.channel.uring.IOUringEventLoopGroup
import zhttp.service.{ChannelFuture, EventLoopGroup}
import zio.{Task, UIO, ZLayer, ZManaged}

import java.util.concurrent.Executor

/**
 * Simple wrapper over NioEventLoopGroup
 */
object EventLoopGroupN {
  def nio(nThreads: Int = 0): ZLayer[Any, Nothing, EventLoopGroup] = EventLoopGroupN.Live.nio(nThreads).toLayer

  def epoll(nThreads: Int = 0): ZLayer[Any, Nothing, EventLoopGroup] = EventLoopGroupN.Live.epoll(nThreads).toLayer

  def uring(nThreads: Int = 0): ZLayer[Any, Nothing, EventLoopGroup] = EventLoopGroupN.Live.uring(nThreads).toLayer

  def auto(nThreads: Int = 0): ZLayer[Any, Nothing, EventLoopGroup] = EventLoopGroupN.Live.auto(nThreads).toLayer

  def default: ZLayer[Any, Nothing, EventLoopGroup] = EventLoopGroupN.Live.default.toLayer

  def nioTask(nThreads: Int = 0): Task[io.netty.channel.EventLoopGroup] = EventLoopGroupN.Live.nioTask(nThreads)

  def epollTask(nThreads: Int = 0): Task[io.netty.channel.EventLoopGroup] = EventLoopGroupN.Live.epollTask(nThreads)

  def uringTask(nThreads: Int = 0): Task[io.netty.channel.EventLoopGroup] = EventLoopGroupN.Live.uringTask(nThreads)

  def autoTask(nThreads: Int = 0): Task[io.netty.channel.EventLoopGroup] = EventLoopGroupN.Live.autoTask(nThreads)

  def defaultTask: Task[io.netty.channel.EventLoopGroup] = EventLoopGroupN.Live.defaultTask

  object Live {
    def nio(nThreads: Int): ZManaged[Any, Nothing, channel.EventLoopGroup] =
      make(UIO(new channel.nio.NioEventLoopGroup(nThreads)))

    def nioTask(nThreads: Int): Task[channel.EventLoopGroup] =
      Task(new channel.nio.NioEventLoopGroup(nThreads))

    def nio(nThreads: Int, executor: Executor): ZManaged[Any, Nothing, channel.EventLoopGroup] =
      make(UIO(new channel.nio.NioEventLoopGroup(nThreads, executor)))

    def make(eventLoopGroup: UIO[channel.EventLoopGroup]): ZManaged[Any, Nothing, channel.EventLoopGroup] =
      eventLoopGroup.toManaged(ev => ChannelFuture.unit(ev.shutdownGracefully).orDie)

    def epoll(nThreads: Int): ZManaged[Any, Nothing, channel.EventLoopGroup] =
      make(UIO(new channel.epoll.EpollEventLoopGroup(nThreads)))

    def epollTask(nThreads: Int): Task[channel.EventLoopGroup] =
      Task(new EpollEventLoopGroup(nThreads))

    def kQueue(nThreads: Int): ZManaged[Any, Nothing, channel.EventLoopGroup] =
      make(UIO(new channel.kqueue.KQueueEventLoopGroup(nThreads)))

    def kQueueTask(nThreads: Int): Task[channel.EventLoopGroup] =
      Task(new KQueueEventLoopGroup(nThreads))

    def epoll(nThreads: Int, executor: Executor): ZManaged[Any, Nothing, channel.EventLoopGroup] =
      make(UIO(new channel.epoll.EpollEventLoopGroup(nThreads, executor)))

    def uring(nThread: Int): ZManaged[Any, Nothing, channel.EventLoopGroup] =
      make(UIO(new IOUringEventLoopGroup(nThread)))

    def uringTask(nThreads: Int): Task[channel.EventLoopGroup] =
      Task(new IOUringEventLoopGroup(nThreads))

    def uring(nThread: Int, executor: Executor): ZManaged[Any, Nothing, channel.EventLoopGroup] =
      make(UIO(new IOUringEventLoopGroup(nThread, executor)))

    def auto(nThreads: Int): ZManaged[Any, Nothing, channel.EventLoopGroup] =
      if (Epoll.isAvailable)
        epoll(nThreads)
      else if (KQueue.isAvailable)
        kQueue(nThreads)
      else nio(nThreads)

    def autoTask(nThreads: Int): Task[channel.EventLoopGroup] =
      if (Epoll.isAvailable)
        epollTask(nThreads)
      else if (KQueue.isAvailable)
        kQueueTask(nThreads)
      else nioTask(nThreads)

    def default: ZManaged[Any, Nothing, channel.EventLoopGroup] = make(UIO(new channel.DefaultEventLoopGroup()))

    def defaultTask: Task[channel.EventLoopGroup] = Task(new channel.DefaultEventLoopGroup())
  }

}
