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

import scala.annotation.experimental

import zio.blocks.chunk.Chunk

final case class Priority(dependency: Int, weight: Int, exclusive: Boolean)

sealed trait H2Frame extends Product with Serializable {
  def streamId: Int
}

@experimental
object H2Frame {
  final case class Data(streamId: Int, data: Chunk[Byte], endStream: Boolean, padLength: Int = 0) extends H2Frame

  final case class Headers(
    streamId: Int,
    headerBlock: Chunk[Byte],
    endStream: Boolean,
    endHeaders: Boolean,
    priority: Option[zio.http.h2.Priority] = None,
    padLength: Int = 0,
  ) extends H2Frame

  final case class Priority(streamId: Int, dependency: Int, weight: Int, exclusive: Boolean) extends H2Frame

  final case class RstStream(streamId: Int, errorCode: H2Error.Code) extends H2Frame

  final case class Settings(ack: Boolean, settings: List[Setting]) extends H2Frame {
    val streamId: Int = 0
  }

  final case class PushPromise(
    streamId: Int,
    promisedStreamId: Int,
    headerBlock: Chunk[Byte],
    endHeaders: Boolean,
    padLength: Int = 0,
  ) extends H2Frame

  final case class Ping(ack: Boolean, data: Chunk[Byte]) extends H2Frame {
    val streamId: Int = 0
  }

  final case class GoAway(lastStreamId: Int, errorCode: H2Error.Code, debugData: Chunk[Byte]) extends H2Frame {
    val streamId: Int = 0
  }

  final case class WindowUpdate(streamId: Int, increment: Int) extends H2Frame

  final case class Continuation(streamId: Int, headerBlock: Chunk[Byte], endHeaders: Boolean) extends H2Frame
}
