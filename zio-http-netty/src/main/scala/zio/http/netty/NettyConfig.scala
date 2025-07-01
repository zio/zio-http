
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

import zio._
import zio.stacktracer.TracingImplicits.disableAutoTrace

import java.time.Duration as JDuration

/**
 * Configuration for Netty server
 */
final case class NettyConfig(
  leakDetectionLevel: LeakDetectionLevel = LeakDetectionLevel.SIMPLE,
  channelType: ChannelType = ChannelType.AUTO,
  nThreads: Int = 0,
  shutdownQuietPeriodDuration: Duration = 2.seconds,
  shutdownTimeoutDuration: Duration = 15.seconds,
) extends Product
    with Serializable {

  def channelType(channelType: ChannelType): NettyConfig =
    copy(channelType = channelType)

  def leakDetectionLevel(leakDetectionLevel: LeakDetectionLevel): NettyConfig =
    copy(leakDetectionLevel = leakDetectionLevel)

  def nThreads(nThreads: Int): NettyConfig =
    copy(nThreads = nThreads)

  def shutdownQuietPeriodDuration(duration: Duration): NettyConfig =
    copy(shutdownQuietPeriodDuration = duration)

  def shutdownTimeoutDuration(duration: Duration): NettyConfig =
    copy(shutdownTimeoutDuration = duration)

}

object NettyConfig {
  val default: NettyConfig = NettyConfig()

  val live: ULayer[NettyConfig] = ZLayer.succeed(default)

  implicit val tag: Tag[NettyConfig] = Tag[NettyConfig]
}

sealed trait LeakDetectionLevel extends Product with Serializable

object LeakDetectionLevel {
  case object DISABLED  extends LeakDetectionLevel
  case object SIMPLE    extends LeakDetectionLevel
  case object ADVANCED  extends LeakDetectionLevel
  case object PARANOID  extends LeakDetectionLevel
}

sealed trait ChannelType extends Product with Serializable

object ChannelType {
  case object AUTO     extends ChannelType
  case object NIO      extends ChannelType
  case object EPOLL    extends ChannelType
  case object KQUEUE   extends ChannelType
  case object IO_URING extends ChannelType
}
