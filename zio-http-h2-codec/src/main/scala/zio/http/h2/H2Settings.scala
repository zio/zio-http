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

package zio.http.h2

final case class Setting(id: Int, value: Long)

object Setting {
  val HEADER_TABLE_SIZE: Int      = 0x1
  val ENABLE_PUSH: Int            = 0x2
  val MAX_CONCURRENT_STREAMS: Int = 0x3
  val INITIAL_WINDOW_SIZE: Int    = 0x4
  val MAX_FRAME_SIZE: Int         = 0x5
  val MAX_HEADER_LIST_SIZE: Int   = 0x6
}

object H2Settings {
  val DefaultHeaderTableSize: Long              = 4096L
  val DefaultEnablePush: Long                   = 1L
  val DefaultInitialWindowSize: Long            = 65535L
  val DefaultMaxFrameSize: Long                 = 16384L
  val MinimumMaxFrameSize: Long                 = 16384L
  val MaximumMaxFrameSize: Long                 = 16777215L
  val DefaultMaxConcurrentStreams: Option[Long] = None
  val DefaultMaxHeaderListSize: Option[Long]    = None
  val DefaultSettings: List[Setting]            = List(
    Setting(Setting.HEADER_TABLE_SIZE, DefaultHeaderTableSize),
    Setting(Setting.ENABLE_PUSH, DefaultEnablePush),
    Setting(Setting.INITIAL_WINDOW_SIZE, DefaultInitialWindowSize),
    Setting(Setting.MAX_FRAME_SIZE, DefaultMaxFrameSize),
  )
  val KnownIdentifiers: Set[Int]                = Set(
    Setting.HEADER_TABLE_SIZE,
    Setting.ENABLE_PUSH,
    Setting.MAX_CONCURRENT_STREAMS,
    Setting.INITIAL_WINDOW_SIZE,
    Setting.MAX_FRAME_SIZE,
    Setting.MAX_HEADER_LIST_SIZE,
  )
}
