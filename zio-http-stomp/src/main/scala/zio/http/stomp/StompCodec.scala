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

package zio.http.stomp

import zio._

import zio.stream.ZPipeline

import zio.schema.codec._

/**
 * Binary codec for STOMP frames for use with WebSocket connections
 */
object StompCodec {

  implicit val binaryCodec: BinaryCodec[StompFrame] = new BinaryCodec[StompFrame] {

    override def decode(whole: Chunk[Byte]): Either[DecodeError, StompFrame] =
      StompFrame.decode(whole).left.map(err => DecodeError.ReadError(Cause.empty, err))

    override def streamDecoder: ZPipeline[Any, DecodeError, Byte, StompFrame] =
      StompFrame.streamDecoder.mapError(err => DecodeError.ReadError(Cause.empty, err))

    override def encode(value: StompFrame): Chunk[Byte] =
      value.encode

    override def streamEncoder: ZPipeline[Any, Nothing, StompFrame, Byte] =
      StompFrame.streamEncoder
  }
}
