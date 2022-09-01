package zhttp.service

import io.netty.channel
import io.netty.channel.epoll.Epoll
import io.netty.channel.kqueue.KQueue
import io.netty.incubator.channel.uring.IOUringEventLoopGroup
import zhttp.service.ChannelModel.ChannelType
import zio._

import java.util.concurrent.Executor

/**
 * Simple wrapper over NioEventLoopGroup
 */
object EventLoopGroup {

  object Live {

    def get(serverChannelType: ChannelType): Int => ZIO[Scope, Nothing, channel.EventLoopGroup] =
      serverChannelType match {
        case ChannelType.NIO    => nio
        case ChannelType.EPOLL  => epoll
        case ChannelType.URING  => uring
        case ChannelType.KQUEUE => kQueue
        case ChannelType.AUTO   => auto
      }

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
