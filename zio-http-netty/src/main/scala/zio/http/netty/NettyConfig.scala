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

import java.util.concurrent.TimeUnit

import zio.{Config, Duration}

import zio.http.netty.NettyConfig.LeakDetectionLevel

import io.netty.util.ResourceLeakDetector

final case class NettyConfig(
  leakDetectionLevel: LeakDetectionLevel,
  channelType: ChannelType,
  nThreads: Int,
  shutdownQuietPeriodDuration: Duration,
  shutdownTimeoutDuration: Duration,
  bossGroup: NettyConfig.BossGroup,
) extends EventLoopGroups.Config { self =>

  /**
   * Configure Netty's boss event-loop group. This only applies to server
   * applications and is ignored for the Client
   */
  def bossGroup(cfg: NettyConfig.BossGroup): NettyConfig = self.copy(bossGroup = cfg)

  def channelType(channelType: ChannelType): NettyConfig = self.copy(channelType = channelType)

  /**
   * Configure Netty to use the leak detection level provided.
   *
   * @see
   *   <a
   *   href="https://netty.io/4.1/api/io/netty/util/ResourceLeakDetector.Level.html">ResourceLeakDetector.Level</a>
   */
  def leakDetection(level: LeakDetectionLevel): NettyConfig = self.copy(leakDetectionLevel = level)

  /**
   * Configure Netty to use a maximum of `nThreads` for the worker event-loop
   * group.
   */
  def maxThreads(nThreads: Int): NettyConfig = self.copy(nThreads = nThreads)

  def shutdownTimeUnit: TimeUnit = TimeUnit.MILLISECONDS
  def shutdownQuietPeriod: Long  = shutdownQuietPeriodDuration.toMillis
  def shutdownTimeOut: Long      = shutdownTimeoutDuration.toMillis
}

object NettyConfig {
  final case class BossGroup(
    channelType: ChannelType,
    nThreads: Int,
    shutdownQuietPeriodDuration: Duration,
    shutdownTimeOutDuration: Duration,
  ) extends EventLoopGroups.Config {
    def shutdownTimeUnit: TimeUnit = TimeUnit.MILLISECONDS
    def shutdownQuietPeriod: Long  = shutdownQuietPeriodDuration.toMillis
    def shutdownTimeOut: Long      = shutdownTimeOutDuration.toMillis
  }

  private def baseConfig: Config[EventLoopGroups.Config] =
    (Config
      .string("channel-type")
      .mapOrFail {
        case "auto"   => Right(ChannelType.AUTO)
        case "nio"    => Right(ChannelType.NIO)
        case "epoll"  => Right(ChannelType.EPOLL)
        case "kqueue" => Right(ChannelType.KQUEUE)
        case "uring"  => Right(ChannelType.URING)
        case other    => Left(Config.Error.InvalidData(message = s"Invalid channel type: $other"))
      }
      .withDefault(NettyConfig.default.channelType) ++
      Config.int("max-threads").withDefault(NettyConfig.default.nThreads) ++
      Config.duration("shutdown-quiet-period").withDefault(NettyConfig.default.shutdownQuietPeriodDuration) ++
      Config.duration("shutdown-timeout").withDefault(NettyConfig.default.shutdownTimeoutDuration)).map {
      case (channelT, maxThreads, quietPeriod, timeout) =>
        new EventLoopGroups.Config {
          override val channelType: ChannelType   = channelT
          override val nThreads: Int              = maxThreads
          override val shutdownQuietPeriod: Long  = quietPeriod.toMillis
          override val shutdownTimeOut: Long      = timeout.toMillis
          override val shutdownTimeUnit: TimeUnit = TimeUnit.MILLISECONDS
        }
    }

  def config: Config[NettyConfig] =
    (LeakDetectionLevel.config.nested("leak-detection-level").withDefault(NettyConfig.default.leakDetectionLevel) ++
      baseConfig.nested("worker-group").orElse(baseConfig) ++
      baseConfig.nested("boss-group")).map { case (leakDetectionLevel, worker, boss) =>
      def toDuration(n: Long, timeUnit: TimeUnit) = Duration.fromJava(java.time.Duration.of(n, timeUnit.toChronoUnit))
      NettyConfig(
        leakDetectionLevel,
        worker.channelType,
        worker.nThreads,
        shutdownQuietPeriodDuration = toDuration(worker.shutdownQuietPeriod, worker.shutdownTimeUnit),
        shutdownTimeoutDuration = toDuration(worker.shutdownTimeOut, worker.shutdownTimeUnit),
        NettyConfig.BossGroup(
          boss.channelType,
          boss.nThreads,
          shutdownQuietPeriodDuration = toDuration(boss.shutdownQuietPeriod, boss.shutdownTimeUnit),
          shutdownTimeOutDuration = toDuration(boss.shutdownTimeOut, boss.shutdownTimeUnit),
        ),
      )
    }

  val default: NettyConfig = {
    val quietPeriod = Duration.fromSeconds(2)
    val timeout     = Duration.fromSeconds(15)
    NettyConfig(
      LeakDetectionLevel.SIMPLE,
      ChannelType.AUTO,
      java.lang.Runtime.getRuntime.availableProcessors(),
      // Defaults taken from io.netty.util.concurrent.AbstractEventExecutor
      shutdownQuietPeriodDuration = quietPeriod,
      shutdownTimeoutDuration = timeout,
      NettyConfig.BossGroup(
        ChannelType.AUTO,
        1,
        shutdownQuietPeriodDuration = quietPeriod,
        shutdownTimeOutDuration = timeout,
      ),
    )
  }

  val defaultWithFastShutdown: NettyConfig = {
    val quietPeriod = Duration.fromMillis(50)
    val timeout     = Duration.fromMillis(250)
    default.copy(
      shutdownQuietPeriodDuration = quietPeriod,
      shutdownTimeoutDuration = timeout,
      bossGroup = default.bossGroup.copy(
        shutdownQuietPeriodDuration = quietPeriod,
        shutdownTimeOutDuration = timeout,
      ),
    )
  }

  sealed trait LeakDetectionLevel {
    self =>
    private[netty] def toNetty: ResourceLeakDetector.Level = self match {
      case LeakDetectionLevel.DISABLED => ResourceLeakDetector.Level.DISABLED
      case LeakDetectionLevel.SIMPLE   => ResourceLeakDetector.Level.SIMPLE
      case LeakDetectionLevel.ADVANCED => ResourceLeakDetector.Level.ADVANCED
      case LeakDetectionLevel.PARANOID => ResourceLeakDetector.Level.PARANOID
    }
  }

  object LeakDetectionLevel {
    case object DISABLED extends LeakDetectionLevel

    case object SIMPLE extends LeakDetectionLevel

    case object ADVANCED extends LeakDetectionLevel

    case object PARANOID extends LeakDetectionLevel

    def config: Config[LeakDetectionLevel] =
      Config.string.mapOrFail {
        case "disabled" => Right(LeakDetectionLevel.DISABLED)
        case "simple"   => Right(LeakDetectionLevel.SIMPLE)
        case "advanced" => Right(LeakDetectionLevel.ADVANCED)
        case "paranoid" => Right(LeakDetectionLevel.PARANOID)
        case other      => Left(Config.Error.InvalidData(message = s"Invalid leak detection level: $other"))
      }
  }
}
