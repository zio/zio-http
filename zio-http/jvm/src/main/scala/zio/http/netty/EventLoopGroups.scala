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
import io.netty.channel.epoll.{Epoll, EpollIoHandler}
import io.netty.channel.kqueue.{KQueue, KQueueIoHandler}
import io.netty.channel.local.LocalIoHandler
import io.netty.channel.nio.NioIoHandler
import io.netty.channel.uring.IoUringIoHandler

/**
 * Creates Netty event loop groups (`MultiThreadIoEventLoopGroup`) for the NIO,
 * epoll, kqueue, io_uring and local transports. When its scope closes, each
 * group is shut down gracefully with the shutdown settings of its
 * [[EventLoopGroups.Config]], and the scope waits until the group has
 * terminated.
 */
object EventLoopGroups {

  /**
   * The settings of one event loop group: its transport, its thread count and
   * the timing of its graceful shutdown.
   */
  trait Config extends ChannelType.Config {

    /**
     * The number of event loops (threads) in the group. With 0, Netty uses its
     * default, twice the number of available processors.
     */
    def nThreads: Int

    /**
     * The quiet period of the graceful shutdown, in `shutdownTimeUnit`: Netty's
     * `shutdownGracefully` waits until no task has been submitted for this
     * long.
     */
    def shutdownQuietPeriod: Long

    /**
     * The longest time, in `shutdownTimeUnit`, that the graceful shutdown waits
     * before the group shuts down, even if tasks are still being submitted.
     */
    def shutdownTimeOut: Long

    /** The unit of `shutdownQuietPeriod` and `shutdownTimeOut`. */
    def shutdownTimeUnit: TimeUnit
  }

  /**
   * Creates an event loop group on the NIO transport with `config.nThreads`
   * threads.
   */
  def nio(config: Config)(implicit trace: Trace): ZIO[Scope, Nothing, EventLoopGroup] =
    make(config: Config, ZIO.succeed(new MultiThreadIoEventLoopGroup(config.nThreads, NioIoHandler.newFactory())))

  /**
   * Creates an event loop group on the NIO transport with `config.nThreads`
   * threads, which run on `executor`.
   */
  def nio(config: Config, executor: Executor)(implicit trace: Trace): ZIO[Scope, Nothing, EventLoopGroup] =
    make(config, ZIO.succeed(new MultiThreadIoEventLoopGroup(config.nThreads, executor, NioIoHandler.newFactory())))

  /**
   * Acquires the group that `eventLoopGroup` creates. When the scope closes,
   * shuts it down gracefully with the shutdown settings of `config` and waits
   * until it has terminated; a failed shutdown is a defect.
   */
  def make(config: Config, eventLoopGroup: UIO[EventLoopGroup])(implicit
    trace: Trace,
  ): ZIO[Scope, Nothing, EventLoopGroup] =
    ZIO.acquireRelease(eventLoopGroup) { ev =>
      val future = ev.shutdownGracefully(config.shutdownQuietPeriod, config.shutdownTimeOut, config.shutdownTimeUnit)
      NettyFutureExecutor
        .executed(future)
        .orDie
    }

  /**
   * Creates an event loop group on the native epoll transport (Linux) with
   * `config.nThreads` threads.
   */
  def epoll(config: Config)(implicit trace: Trace): ZIO[Scope, Nothing, EventLoopGroup] =
    make(config, ZIO.succeed(new MultiThreadIoEventLoopGroup(config.nThreads, EpollIoHandler.newFactory())))

  /**
   * Creates an event loop group on the native kqueue transport (macOS) with
   * `config.nThreads` threads.
   */
  def kqueue(config: Config)(implicit trace: Trace): ZIO[Scope, Nothing, EventLoopGroup] =
    make(config, ZIO.succeed(new MultiThreadIoEventLoopGroup(config.nThreads, KQueueIoHandler.newFactory())))

  /**
   * Creates an event loop group on the native epoll transport (Linux) with
   * `config.nThreads` threads, which run on `executor`.
   */
  def epoll(config: Config, executor: Executor)(implicit trace: Trace): ZIO[Scope, Nothing, EventLoopGroup] =
    make(config, ZIO.succeed(new MultiThreadIoEventLoopGroup(config.nThreads, executor, EpollIoHandler.newFactory())))

  /**
   * Creates an event loop group on the native io_uring transport (Linux) with
   * `config.nThreads` threads. This requires the
   * `io.netty:netty-transport-native-io_uring` dependency, which zio-http
   * declares as `provided`, so the application must add it. Without it, the
   * effect dies with a `java.lang.NoClassDefFoundError`.
   */
  def uring(config: Config)(implicit trace: Trace): ZIO[Scope, Nothing, EventLoopGroup] =
    make(config, ZIO.succeed(new MultiThreadIoEventLoopGroup(config.nThreads, IoUringIoHandler.newFactory())))

  /**
   * Creates an event loop group on the native io_uring transport (Linux) with
   * `config.nThreads` threads, which run on `executor`. This requires the
   * `io.netty:netty-transport-native-io_uring` dependency, which zio-http
   * declares as `provided`, so the application must add it. Without it, the
   * effect dies with a `java.lang.NoClassDefFoundError`.
   */
  def uring(config: Config, executor: Executor)(implicit trace: Trace): ZIO[Scope, Nothing, EventLoopGroup] =
    make(config, ZIO.succeed(new MultiThreadIoEventLoopGroup(config.nThreads, executor, IoUringIoHandler.newFactory())))

  /**
   * Creates an event loop group on Netty's local transport, which connects
   * channels inside one JVM, with Netty's default thread count (twice the
   * number of available processors). `config.nThreads` is not used; the
   * shutdown settings of `config` are.
   */
  def default(config: Config)(implicit trace: Trace): ZIO[Scope, Nothing, EventLoopGroup] = make(
    config,
    ZIO.succeed(new MultiThreadIoEventLoopGroup(LocalIoHandler.newFactory())),
  )

  /** An empty `Trace`, the implicit trace of `live`. */
  implicit val trace: Trace = Trace.empty

  /**
   * A layer that creates the event loop group for the transport that
   * `Config#channelType` names, with `AUTO` picking epoll, then kqueue, then
   * NIO by availability. The group is shut down when the layer's scope closes.
   */
  val live: ZLayer[Config, Nothing, EventLoopGroup] =
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
