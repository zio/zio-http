/*
 * Copyright 2021 - 2023 Sporta Technologies PVT LTD & the ZIO HTTP contributors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package zio.http.netty

import java.util.concurrent.Executor

import zio._
import zio.stacktracer.TracingImplicits.disableAutoTrace

import io.netty.channel._
import io.netty.channel.epoll.{Epoll, EpollEventLoopGroup}
import io.netty.channel.kqueue.{KQueue, KQueueEventLoopGroup}
import io.netty.channel.nio.NioEventLoopGroup
import io.netty.incubator.channel.uring.IOUringEventLoopGroup

/**
 * Simple wrapper over NioEventLoopGroup
 */
private[netty] object EventLoopGroups {
  trait Config extends ChannelType.Config {
    def nThreads: Int
  }

  def nio(nThreads: Int)(implicit trace: Trace): ZIO[Scope, Nothing, EventLoopGroup] =
    make(ZIO.succeed(new NioEventLoopGroup(nThreads)))

  def nio(nThreads: Int, executor: Executor)(implicit trace: Trace): ZIO[Scope, Nothing, EventLoopGroup] =
    make(ZIO.succeed(new NioEventLoopGroup(nThreads, executor)))

  def make(eventLoopGroup: UIO[EventLoopGroup])(implicit trace: Trace): ZIO[Scope, Nothing, EventLoopGroup] =
    ZIO.acquireRelease(eventLoopGroup)(ev => NettyFutureExecutor.executed(ev.shutdownGracefully).orDie)

  def epoll(nThreads: Int)(implicit trace: Trace): ZIO[Scope, Nothing, EventLoopGroup] =
    make(ZIO.succeed(new EpollEventLoopGroup(nThreads)))

  def kqueue(nThreads: Int)(implicit trace: Trace): ZIO[Scope, Nothing, EventLoopGroup] =
    make(ZIO.succeed(new KQueueEventLoopGroup(nThreads)))

  def epoll(nThreads: Int, executor: Executor)(implicit trace: Trace): ZIO[Scope, Nothing, EventLoopGroup] =
    make(ZIO.succeed(new EpollEventLoopGroup(nThreads, executor)))

  def uring(nThread: Int)(implicit trace: Trace): ZIO[Scope, Nothing, EventLoopGroup] =
    make(ZIO.succeed(new IOUringEventLoopGroup(nThread)))

  def uring(nThread: Int, executor: Executor)(implicit trace: Trace): ZIO[Scope, Nothing, EventLoopGroup] =
    make(ZIO.succeed(new IOUringEventLoopGroup(nThread, executor)))

  def default(implicit trace: Trace): ZIO[Scope, Nothing, EventLoopGroup] = make(
    ZIO.succeed(new DefaultEventLoopGroup()),
  )

  implicit val trace: Trace = Trace.empty

  val live: ZLayer[Config, Nothing, EventLoopGroup] =
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
