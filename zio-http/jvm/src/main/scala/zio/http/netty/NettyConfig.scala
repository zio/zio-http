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

import zio.http.netty.NettyConfig.{ExecutionMode, LeakDetectionLevel}

import io.netty.util.ResourceLeakDetector

final case class NettyConfig(
  leakDetectionLevel: LeakDetectionLevel,
  channelType: ChannelType,
  executionMode: ExecutionMode,
  nThreads: Int,
  shutdownQuietPeriodDuration: Duration,
  shutdownTimeoutDuration: Duration,
) extends EventLoopGroups.Config { self =>

  def channelType(channelType: ChannelType): NettyConfig = self.copy(channelType = channelType)

  def executionMode(value: ExecutionMode): NettyConfig = self.copy(executionMode = value)

  /**
   * Configure the server to use the leak detection level provided.
   *
   * @see
   *   <a
   *   href="https://netty.io/4.1/api/io/netty/util/ResourceLeakDetector.Level.html">ResourceLeakDetector.Level</a>
   */
  def leakDetection(level: LeakDetectionLevel): NettyConfig = self.copy(leakDetectionLevel = level)

  /**
   * Configure the server to use a maximum of nThreads to process requests.
   */
  def maxThreads(nThreads: Int): NettyConfig = self.copy(nThreads = nThreads)

  val shutdownTimeUnit: TimeUnit = TimeUnit.MILLISECONDS

  val shutdownQuietPeriod: Long = shutdownQuietPeriodDuration.toMillis
  val shutdownTimeOut: Long     = shutdownTimeoutDuration.toMillis
}

object NettyConfig {
  lazy val config: Config[NettyConfig] =
    (LeakDetectionLevel.config.nested("leak-detection-level").withDefault(NettyConfig.default.leakDetectionLevel) ++
      Config
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
      ExecutionMode.config.nested("execution-mode").withDefault(NettyConfig.default.executionMode) ++
      Config.int("max-threads").withDefault(NettyConfig.default.nThreads) ++
      Config.duration("shutdown-quiet-period").withDefault(NettyConfig.default.shutdownQuietPeriodDuration) ++
      Config.duration("shutdown-timeout").withDefault(NettyConfig.default.shutdownTimeoutDuration)).map {
      case (leakDetectionLevel, channelType, executionMode, maxThreads, quietPeriod, timeout) =>
        NettyConfig(leakDetectionLevel, channelType, executionMode, maxThreads, quietPeriod, timeout)
    }

  lazy val default: NettyConfig = NettyConfig(
    LeakDetectionLevel.SIMPLE,
    ChannelType.AUTO,
    ExecutionMode.Default,
    0,
    // Defaults taken from io.netty.util.concurrent.AbstractEventExecutor
    Duration.fromSeconds(2),
    Duration.fromSeconds(15),
  )

  lazy val defaultWithFastShutdown: NettyConfig = default.copy(
    shutdownQuietPeriodDuration = Duration.fromMillis(50),
    shutdownTimeoutDuration = Duration.fromMillis(250),
  )

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

    lazy val config: Config[LeakDetectionLevel] =
      Config.string.mapOrFail {
        case "disabled" => Right(LeakDetectionLevel.DISABLED)
        case "simple"   => Right(LeakDetectionLevel.SIMPLE)
        case "advanced" => Right(LeakDetectionLevel.ADVANCED)
        case "paranoid" => Right(LeakDetectionLevel.PARANOID)
        case other      => Left(Config.Error.InvalidData(message = s"Invalid leak detection level: $other"))
      }
  }

  sealed trait ExecutionMode

  object ExecutionMode {

    /**
     * Delegate all effect handling to the ZIO runtime
     */
    case object Default extends ExecutionMode

    /**
     * Run requests on Netty's event loop thread until the first async boundary.
     *
     * Selecting this mode can improve performance for short-lived CPU-bound
     * tasks, but can also lead to degraded performance if the request handler
     * performs CPU-heavy work prior to the first async boundary.
     *
     * '''WARNING:''' Do not use this mode if the ZIO executor is configured to
     * use virtual threads
     *
     * @see
     *   For more info on caveats of this mode, see <a
     *   href="https://github.com/zio/zio-http/pull/2782">related issue </a>
     */
    case object MinimizeContextSwitching extends ExecutionMode

    lazy val config: Config[ExecutionMode] =
      Config.string.mapOrFail {
        case "default"                  => Right(ExecutionMode.Default)
        case "minimizeContextSwitching" => Right(ExecutionMode.MinimizeContextSwitching)
        case other                      => Left(Config.Error.InvalidData(message = s"Invalid execution mode: $other"))
      }
  }
}
