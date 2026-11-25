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

/**
 * The Netty transport that a server or client uses for its event loop groups
 * and its channels. Set it with `NettyConfig#channelType`.
 */
sealed trait ChannelType

/** The transports that `ChannelType` can name. */
object ChannelType {

  /**
   * Netty's NIO transport, built on Java NIO selectors. It needs no native
   * library.
   */
  case object NIO extends ChannelType

  /**
   * Netty's native epoll transport for Linux. zio-http depends on its native
   * libraries for `linux-x86_64` and `linux-aarch_64`.
   */
  case object EPOLL extends ChannelType

  /**
   * Netty's native kqueue transport for macOS. zio-http depends on its native
   * libraries for `osx-x86_64` and `osx-aarch_64`.
   */
  case object KQUEUE extends ChannelType

  /**
   * Netty's native io_uring transport for Linux. zio-http declares
   * `io.netty:netty-transport-native-io_uring` as a `provided` dependency, so
   * the application must add it. Without it, building the event loop group dies
   * with a `java.lang.NoClassDefFoundError` for
   * `io/netty/channel/uring/IoUringIoHandler`.
   */
  case object URING extends ChannelType

  /**
   * Picks `EPOLL` when Netty reports epoll as available, else `KQUEUE` when
   * kqueue is available, else `NIO`.
   */
  case object AUTO extends ChannelType

  /** A configuration that names a [[ChannelType]]. */
  trait Config {

    /** The transport to use. */
    def channelType: ChannelType
  }

}
