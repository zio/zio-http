
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

package zio.http.netty.server

import zio._
import zio.http._
import zio.http.netty.NettyConfig
import zio.stacktracer.TracingImplicits.disableAutoTrace

import io.netty.bootstrap.ServerBootstrap
import io.netty.channel._
import io.netty.channel.epoll.{EpollEventLoopGroup, EpollServerSocketChannel}
import io.netty.channel.kqueue.{KqueueEventLoopGroup, KqueueServerSocketChannel}
import io.netty.channel.nio.NioEventLoopGroup
import io.netty.channel.socket.nio.NioServerSocketChannel
import io.netty.handler.logging.{LogLevel, LoggingHandler}
import io.netty.util.ResourceLeakDetector

final case class NettyDriver(
  eventLoopGroup: EventLoopGroup,
  channelFactory: ChannelFactory[ServerChannel],
  shutdown: UIO[Unit],
) extends Driver {

  override def start(implicit trace: Trace): RIO[Scope, StartResult] = {
    val serverBootstrap = new ServerBootstrap().group(eventLoopGroup).channelFactory(channelFactory)
    
    ZIO.succeed(StartResult.make(serverBootstrap, shutdown))
  }
}

object NettyDriver {

  def live: ZLayer[NettyConfig, Throwable, NettyDriver] =
    ZLayer.scoped {
      for {
        config <- ZIO.service[NettyConfig]
        driver <- make(config)
      } yield driver
    }

  def make(config: NettyConfig)(implicit trace: Trace): ZIO[Scope, Throwable, NettyDriver] = {
    ZIO.acquireRelease {
      ZIO.attempt {
        // Set leak detection level
        ResourceLeakDetector.setLevel(convertLeakDetectionLevel(config.leakDetectionLevel))

        val (eventLoopGroup, channelClass) = config.channelType match {
          case ChannelType.EPOLL =>
            val group = new EpollEventLoopGroup(config.nThreads)
            (group, () => new EpollServerSocketChannel())

          case ChannelType.KQUEUE =>
            val group = new KqueueEventLoopGroup(config.nThreads)
            (group, () => new KqueueServerSocketChannel())

          case ChannelType.NIO | ChannelType.AUTO =>
            val group = new NioEventLoopGroup(config.nThreads)
            (group, () => new NioServerSocketChannel())

          case ChannelType.IO_URING =>
            throw new UnsupportedOperationException("IO_URING not yet supported")
        }

        val channelFactory = new ChannelFactory[ServerChannel] {
          override def newChannel(): ServerChannel = channelClass()
        }

        val shutdown = ZIO.attempt {
          val future = eventLoopGroup.shutdownGracefully(
            config.shutdownQuietPeriodDuration.toNanos,
            config.shutdownTimeoutDuration.toNanos,
            java.util.concurrent.TimeUnit.NANOSECONDS,
          )
          future.sync()
        }.ignore

        NettyDriver(eventLoopGroup, channelFactory, shutdown)
      }
    } { driver =>
      driver.shutdown
    }
  }

  private def convertLeakDetectionLevel(level: LeakDetectionLevel): ResourceLeakDetector.Level =
    level match {
      case LeakDetectionLevel.DISABLED => ResourceLeakDetector.Level.DISABLED
      case LeakDetectionLevel.SIMPLE   => ResourceLeakDetector.Level.SIMPLE
      case LeakDetectionLevel.ADVANCED => ResourceLeakDetector.Level.ADVANCED
      case LeakDetectionLevel.PARANOID => ResourceLeakDetector.Level.PARANOID
    }
}

case class StartResult(bootstrap: ServerBootstrap, shutdown: UIO[Unit])

object StartResult {
  def make(bootstrap: ServerBootstrap, shutdown: UIO[Unit]): StartResult =
    StartResult(bootstrap, shutdown)
}
