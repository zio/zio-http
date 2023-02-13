package zio.http.netty

import io.netty.channel._
import io.netty.channel.epoll.{Epoll, EpollEventLoopGroup}
import io.netty.channel.kqueue.{KQueue, KQueueEventLoopGroup}
import io.netty.channel.nio.NioEventLoopGroup
import io.netty.incubator.channel.uring.IOUringEventLoopGroup
import zio._

import java.util.concurrent.Executor

/**
 * Simple wrapper over NioEventLoopGroup
 */
object EventLoopGroups {
  trait Config extends ChannelType.Config {
    def nThreads: Int
  }

  def nio(nThreads: Int): ZIO[Scope, Nothing, EventLoopGroup] =
    make(ZIO.succeed(new NioEventLoopGroup(nThreads)))

  def nio(nThreads: Int, executor: Executor): ZIO[Scope, Nothing, EventLoopGroup] =
    make(ZIO.succeed(new NioEventLoopGroup(nThreads, executor)))

  def make(eventLoopGroup: UIO[EventLoopGroup]): ZIO[Scope, Nothing, EventLoopGroup] =
    ZIO.acquireRelease(eventLoopGroup)(ev => NettyFutureExecutor.executed(ev.shutdownGracefully).orDie)

  def epoll(nThreads: Int): ZIO[Scope, Nothing, EventLoopGroup] =
    make(ZIO.succeed(new EpollEventLoopGroup(nThreads)))

  def kqueue(nThreads: Int): ZIO[Scope, Nothing, EventLoopGroup] =
    make(ZIO.succeed(new KQueueEventLoopGroup(nThreads)))

  def epoll(nThreads: Int, executor: Executor): ZIO[Scope, Nothing, EventLoopGroup] =
    make(ZIO.succeed(new EpollEventLoopGroup(nThreads, executor)))

  def uring(nThread: Int): ZIO[Scope, Nothing, EventLoopGroup] =
    make(ZIO.succeed(new IOUringEventLoopGroup(nThread)))

  def uring(nThread: Int, executor: Executor): ZIO[Scope, Nothing, EventLoopGroup] =
    make(ZIO.succeed(new IOUringEventLoopGroup(nThread, executor)))

  def default: ZIO[Scope, Nothing, EventLoopGroup] = make(
    ZIO.succeed(new DefaultEventLoopGroup()),
  )

  val fromConfig: ZLayer[Config, Nothing, EventLoopGroup] =
    ZLayer.scoped {
      ZIO.service[Config].flatMap { config =>
        config.channelType match {
          case ChannelType.NIO    => nio(config.nThreads)
          case ChannelType.EPOLL  => epoll(config.nThreads)
          case ChannelType.KQUEUE => kqueue(config.nThreads)
          case ChannelType.URING  => uring(config.nThreads)
          case ChannelType.AUTO   =>
            if (Epoll.isAvailable)
              epoll(config.nThreads)
            else if (KQueue.isAvailable)
              kqueue(config.nThreads)
            else nio(config.nThreads)
        }
      }
    }

}
