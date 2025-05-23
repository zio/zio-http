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

sealed trait ChannelType

object ChannelType {
  case object NIO extends ChannelType

  case object EPOLL extends ChannelType

  case object KQUEUE extends ChannelType

  /**
   * Note using URING is experimental and requires explicit dependency on
   * netty-incubator-transport-native-io_uring
   */
  case object URING extends ChannelType

  case object AUTO extends ChannelType

  trait Config {
    def channelType: ChannelType
  }

}
