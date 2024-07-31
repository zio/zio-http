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

package zio.http

import zio._

import zio.stream.ZPipeline

import zio.schema.codec.{BinaryCodec, DecodeError}
import zio.schema.{DeriveSchema, Schema}

import zio.http.codec.{BinaryCodecWithSchema, HttpContentCodec}

/**
 * Server-Sent Event (SSE) as defined by
 * https://html.spec.whatwg.org/multipage/server-sent-events.html#server-sent-events
 *
 * @param data
 *   data, may span multiple lines
 * @param eventType
 *   optional type, must not contain \n or \r
 * @param id
 *   optional id, must not contain \n or \r
 * @param retry
 *   optional reconnection delay in milliseconds
 */
final case class ServerSentEvent(
  data: String,
  eventType: Option[String] = None,
  id: Option[String] = None,
  retry: Option[Int] = None,
) {

  def encode: String = {
    val sb = new StringBuilder
    data.linesIterator.foreach { line =>
      sb.append("data: ").append(line).append('\n')
    }
    eventType.foreach { et =>
      sb.append("event: ").append(et.linesIterator.mkString(" ")).append('\n')
    }
    id.foreach { i =>
      sb.append("id: ").append(i.linesIterator.mkString(" ")).append('\n')
    }
    retry.foreach { r =>
      sb.append("retry: ").append(r).append('\n')
    }
    sb.append('\n').toString
  }
}

object ServerSentEvent {
  implicit lazy val schema: Schema[ServerSentEvent] = DeriveSchema.gen[ServerSentEvent]

  implicit val contentCodec: HttpContentCodec[ServerSentEvent] = HttpContentCodec.from(
    MediaType.text.`event-stream` -> BinaryCodecWithSchema.fromBinaryCodec(new BinaryCodec[ServerSentEvent] {
      override def decode(whole: Chunk[Byte]): Either[DecodeError, ServerSentEvent] =
        throw new UnsupportedOperationException("ServerSentEvent decoding is not yet supported.")

      override def streamDecoder: ZPipeline[Any, DecodeError, Byte, ServerSentEvent] =
        throw new UnsupportedOperationException("ServerSentEvent decoding is not yet supported.")

      override def encode(value: ServerSentEvent): Chunk[Byte] =
        Chunk.fromArray(value.encode.getBytes)

      override def streamEncoder: ZPipeline[Any, Nothing, ServerSentEvent, Byte] =
        ZPipeline.mapChunks(value => value.flatMap(c => c.encode.getBytes))
    }),
  )

  def heartbeat: ServerSentEvent = new ServerSentEvent("")
}
