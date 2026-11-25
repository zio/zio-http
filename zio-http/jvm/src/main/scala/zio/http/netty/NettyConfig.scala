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

/**
 * The Netty settings of a zio-http server or client: the transport, the number
 * of worker threads, the timing of the graceful shutdown, the leak detection
 * level, and the server's boss group. Pass it with a `Server.Config` to
 * `Server.customized`, or with a `ZClient.Config` to `ZClient.live`.
 * `Server.live` uses `NettyConfig.default`, and `ZClient.default` uses
 * `NettyConfig.defaultWithFastShutdown`.
 *
 * {{{
 * val config: NettyConfig =
 *   NettyConfig.default
 *     .channelType(ChannelType.NIO)
 *     .maxThreads(4)
 *
 * config.nThreads // 4
 * }}}
 *
 * @param leakDetectionLevel
 *   the level that the server sets on Netty's `ResourceLeakDetector` when it
 *   starts. The level applies to the whole JVM; the client does not set it.
 * @param channelType
 *   the transport of the worker event loop group and of the channels
 * @param nThreads
 *   the number of threads of the worker event loop group, or of the client's
 *   event loop group. With 0, Netty uses its default, twice the number of
 *   available processors.
 * @param shutdownQuietPeriodDuration
 *   the quiet period of the graceful shutdown of the worker event loop group
 * @param shutdownTimeoutDuration
 *   the longest time the graceful shutdown of the worker event loop group waits
 * @param bossGroup
 *   the settings of the server's boss event loop group
 * @see
 *   [[https://zio.dev/zio-http/ ZIO HTTP]]
 */
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

  /**
   * Returns a copy that uses `channelType` for the worker event loop group and
   * the channels. The server's boss group takes its transport from `bossGroup`.
   */
  def channelType(channelType: ChannelType): NettyConfig = self.copy(channelType = channelType)

  /**
   * Returns a copy with the given leak detection level. The server passes it to
   * Netty's `ResourceLeakDetector.setLevel` when it starts, which sets the
   * level for the whole JVM; the client does not read it.
   *
   * @see
   *   <a
   *   href="https://netty.io/4.2/api/io/netty/util/ResourceLeakDetector.Level.html">ResourceLeakDetector.Level</a>
   */
  def leakDetection(level: LeakDetectionLevel): NettyConfig = self.copy(leakDetectionLevel = level)

  /**
   * Returns a copy that uses `nThreads` threads for the worker event loop
   * group, or for the client's event loop group. With 0, Netty uses its
   * default, twice the number of available processors.
   */
  def maxThreads(nThreads: Int): NettyConfig = self.copy(nThreads = nThreads)

  /** Always `TimeUnit.MILLISECONDS`. */
  def shutdownTimeUnit: TimeUnit = TimeUnit.MILLISECONDS

  /** `shutdownQuietPeriodDuration` in milliseconds. */
  def shutdownQuietPeriod: Long = shutdownQuietPeriodDuration.toMillis

  /** `shutdownTimeoutDuration` in milliseconds. */
  def shutdownTimeOut: Long = shutdownTimeoutDuration.toMillis
}

/** Constructors and defaults for [[NettyConfig]]. */
object NettyConfig {

  /**
   * The settings of the server's boss event loop group, which accepts the
   * incoming connections and hands them to the worker group.
   * `NettyConfig.default` gives it one thread.
   */
  final case class BossGroup(
    channelType: ChannelType,
    nThreads: Int,
    shutdownQuietPeriodDuration: Duration,
    shutdownTimeOutDuration: Duration,
  ) extends EventLoopGroups.Config {

    /** Always `TimeUnit.MILLISECONDS`. */
    def shutdownTimeUnit: TimeUnit = TimeUnit.MILLISECONDS

    /** `shutdownQuietPeriodDuration` in milliseconds. */
    def shutdownQuietPeriod: Long = shutdownQuietPeriodDuration.toMillis

    /** `shutdownTimeOutDuration` in milliseconds. */
    def shutdownTimeOut: Long = shutdownTimeOutDuration.toMillis
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

  /**
   * A `zio.Config` that reads a [[NettyConfig]]. It reads
   * `leak-detection-level`, and the keys `channel-type` (`auto`, `nio`,
   * `epoll`, `kqueue` or `uring`), `max-threads`, `shutdown-quiet-period` and
   * `shutdown-timeout` under `worker-group` for the worker settings and under
   * `boss-group` for the boss group. A missing key takes the worker value of
   * `NettyConfig.default`, so a missing `boss-group.max-threads` gives one
   * thread per available processor, not one.
   *
   * When reading the `worker-group` keys fails, for example on an unknown
   * `channel-type` or a `max-threads` that is not a number, the worker settings
   * are read from the same keys at the top level instead; otherwise the
   * top-level keys are not read. An unknown `boss-group.channel-type`, such as
   * `foo`, fails with `Invalid channel type: foo`.
   */
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

  /**
   * The default settings: the `AUTO` transport, one worker thread per available
   * processor, `SIMPLE` leak detection, a 2 second quiet period and a 15 second
   * shutdown timeout, and a boss group with one thread and the same transport
   * and shutdown timing.
   */
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

  /**
   * `default` with a 50 millisecond quiet period and a 250 millisecond shutdown
   * timeout, for both the worker and the boss group.
   */
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

  /**
   * A level of Netty's `ResourceLeakDetector`, which reports `ByteBuf`s that
   * were garbage collected without being released.
   */
  sealed trait LeakDetectionLevel {
    self =>
    private[netty] def toNetty: ResourceLeakDetector.Level = self match {
      case LeakDetectionLevel.DISABLED => ResourceLeakDetector.Level.DISABLED
      case LeakDetectionLevel.SIMPLE   => ResourceLeakDetector.Level.SIMPLE
      case LeakDetectionLevel.ADVANCED => ResourceLeakDetector.Level.ADVANCED
      case LeakDetectionLevel.PARANOID => ResourceLeakDetector.Level.PARANOID
    }
  }

  /** The levels of leak detection. */
  object LeakDetectionLevel {

    /** Turns leak detection off. */
    case object DISABLED extends LeakDetectionLevel

    /**
     * Netty's `SIMPLE` level: samples buffers and reports whether a leak
     * happened, at a small cost.
     */
    case object SIMPLE extends LeakDetectionLevel

    /**
     * Netty's `ADVANCED` level: samples buffers and reports where a leaked
     * buffer was last accessed, at a higher cost.
     */
    case object ADVANCED extends LeakDetectionLevel

    /**
     * Netty's `PARANOID` level: tracks every buffer and reports where a leaked
     * buffer was last accessed, at the highest cost.
     */
    case object PARANOID extends LeakDetectionLevel

    /**
     * A `zio.Config` that reads a level from one of the lowercase strings
     * `disabled`, `simple`, `advanced` or `paranoid`. Any other string, such as
     * `SIMPLE`, fails with `Invalid leak detection level: SIMPLE`.
     */
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
