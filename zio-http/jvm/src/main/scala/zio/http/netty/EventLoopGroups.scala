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

import scala.concurrent.duration.TimeUnit

import zio._
import zio.stacktracer.TracingImplicits.disableAutoTrace

import io.netty.channel._
import io.netty.channel.epoll.{Epoll, EpollEventLoopGroup}
import io.netty.channel.kqueue.{KQueue, KQueueEventLoopGroup}
import io.netty.channel.nio.NioEventLoopGroup
import io.netty.incubator.channel.uring.IOUringEventLoopGroup

case class EventLoopGroups(parent: EventLoopGroup, child: EventLoopGroup)

object EventLoopGroups {
  trait Config extends ChannelType.Config {
    def nThreads: Int
    def shutdownQuietPeriod: Long
    def shutdownTimeOut: Long

    def shutdownTimeUnit: TimeUnit
  }

  def nio(config: Config)(implicit trace: Trace): ZIO[Scope, Nothing, EventLoopGroups] = {
    val parent = make(config: Config, ZIO.succeed(new NioEventLoopGroup(1)))
    val child  = make(config: Config, ZIO.succeed(new NioEventLoopGroup(config.nThreads)))
    parent.zipWith(child)(EventLoopGroups.apply)
  }

  def nio(config: Config, executor: Executor)(implicit trace: Trace): ZIO[Scope, Nothing, EventLoopGroups] = {
    val parent = make(config, ZIO.succeed(new NioEventLoopGroup(1, executor)))
    val child  = make(config, ZIO.succeed(new NioEventLoopGroup(config.nThreads, executor)))
    parent.zipWith(child)(EventLoopGroups.apply)
  }

  def make(config: Config, eventLoopGroup: UIO[EventLoopGroup])(implicit
    trace: Trace,
  ): ZIO[Scope, Nothing, EventLoopGroup] =
    ZIO.acquireRelease(eventLoopGroup) { ev =>
      val future = ev.shutdownGracefully(config.shutdownQuietPeriod, config.shutdownTimeOut, config.shutdownTimeUnit)
      NettyFutureExecutor
        .executed(future)
        .orDie
    }

  def epoll(config: Config)(implicit trace: Trace): ZIO[Scope, Nothing, EventLoopGroups] = {
    val parent = make(config, ZIO.succeed(new EpollEventLoopGroup(1)))
    val child  = make(config, ZIO.succeed(new EpollEventLoopGroup(config.nThreads)))
    parent.zipWith(child)(EventLoopGroups.apply)
  }

  def kqueue(config: Config)(implicit trace: Trace): ZIO[Scope, Nothing, EventLoopGroups] = {
    val parent = make(config, ZIO.succeed(new KQueueEventLoopGroup(1)))
    val child  = make(config, ZIO.succeed(new KQueueEventLoopGroup(config.nThreads)))
    parent.zipWith(child)(EventLoopGroups.apply)
  }

  def epoll(config: Config, executor: Executor)(implicit trace: Trace): ZIO[Scope, Nothing, EventLoopGroups] = {
    val parent = make(config, ZIO.succeed(new EpollEventLoopGroup(1, executor)))
    val child  = make(config, ZIO.succeed(new EpollEventLoopGroup(config.nThreads, executor)))
    parent.zipWith(child)(EventLoopGroups.apply)
  }

  def uring(config: Config)(implicit trace: Trace): ZIO[Scope, Nothing, EventLoopGroups] = {
    val parent = make(config, ZIO.succeed(new IOUringEventLoopGroup(1)))
    val child  = make(config, ZIO.succeed(new IOUringEventLoopGroup(config.nThreads)))
    parent.zipWith(child)(EventLoopGroups.apply)
  }

  def uring(config: Config, executor: Executor)(implicit trace: Trace): ZIO[Scope, Nothing, EventLoopGroups] = {
    val parent = make(config, ZIO.succeed(new IOUringEventLoopGroup(1, executor)))
    val child  = make(config, ZIO.succeed(new IOUringEventLoopGroup(config.nThreads, executor)))
    parent.zipWith(child)(EventLoopGroups.apply)
  }

  def default(config: Config)(implicit trace: Trace): ZIO[Scope, Nothing, EventLoopGroup] = make(
    config,
    ZIO.succeed(new DefaultEventLoopGroup()),
  )

  implicit val trace: Trace = Trace.empty

  val live: ZLayer[Config, Nothing, EventLoopGroups] =
    ZLayer.scoped {
      ZIO.serviceWithZIO[Config] { config =>
        config.channelType match {
          case ChannelType.NIO    => nio(config)
          case ChannelType.EPOLL  => epoll(config)
          case ChannelType.KQUEUE => kqueue(config)
          case ChannelType.URING  => uring(config)
          case ChannelType.AUTO   =>
            if (Epoll.isAvailable)
              epoll(config)
            else if (KQueue.isAvailable)
              kqueue(config)
            else nio(config)
        }
      }
    }
}
