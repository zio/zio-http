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

import zio.schema.codec._
import zio.schema.{DeriveSchema, Schema}

import zio.http.codec._

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
final case class ServerSentEvent[T](
  data: T,
  eventType: Option[String] = None,
  id: Option[String] = None,
  retry: Option[Int] = None,
) {

  def encode(implicit binaryCodec: BinaryCodec[T]): String = {
    val sb = new StringBuilder
    binaryCodec.encode(data).asString.linesIterator.foreach { line =>
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

  implicit def schema[T](implicit schema: Schema[T]): Schema[ServerSentEvent[T]] = DeriveSchema.gen[ServerSentEvent[T]]

  implicit def defaultBinaryCodec[T](implicit schema: Schema[T]): BinaryCodec[ServerSentEvent[T]] =
    defaultContentCodec(schema).defaultCodec

  private def nextOption(lines: Iterator[String]): Option[String] =
    if (lines.hasNext) Some(lines.next())
    else None

  implicit def binaryCodec[T](implicit binaryCodec: BinaryCodec[T]): BinaryCodec[ServerSentEvent[T]] =
    new BinaryCodec[ServerSentEvent[T]] {
      override def decode(whole: Chunk[Byte]): Either[DecodeError, ServerSentEvent[T]] = {
        val lines     = whole.asString.linesIterator
        val data      = lines.next().stripPrefix("data: ")
        val eventType = nextOption(lines).map(_.stripPrefix("event: "))
        val id        = nextOption(lines).map(_.stripPrefix("id: "))
        val retry     = nextOption(lines).map(_.stripPrefix("retry: ").toInt)
        val decoded   = binaryCodec.decode(Chunk.fromArray(data.getBytes))
        decoded.map(value => ServerSentEvent(value, eventType, id, retry))
      }

      override def streamDecoder: ZPipeline[Any, DecodeError, Byte, ServerSentEvent[T]] =
        ZPipeline.chunks[Byte].map(_.asString) >>> ZPipeline
          .splitOn("\n\n")
          .mapZIO(s => {
            val lines     = s.linesIterator
            val data      = lines.next().stripPrefix("data: ")
            val eventType = nextOption(lines).map(_.stripPrefix("event: "))
            val id        = nextOption(lines).map(_.stripPrefix("id: "))
            val retry     = nextOption(lines).map(_.stripPrefix("retry: ").toInt)
            val decoded   = binaryCodec.decode(Chunk.fromArray(data.getBytes))
            ZIO.fromEither(decoded.map(value => ServerSentEvent(value, eventType, id, retry)))
          })

      override def encode(value: ServerSentEvent[T]): Chunk[Byte] = Chunk.fromArray(value.encode.getBytes)

      override def streamEncoder: ZPipeline[Any, Nothing, ServerSentEvent[T], Byte] =
        ZPipeline.mapChunks(value => value.flatMap(c => c.encode.getBytes))
    }

  implicit def contentCodec[T](implicit
    tCodec: BinaryCodec[T],
    schema: Schema[T],
  ): HttpContentCodec[ServerSentEvent[T]] = HttpContentCodec.from(
    MediaType.text.`event-stream` -> BinaryCodecWithSchema.fromBinaryCodec(binaryCodec),
  )

  implicit def defaultContentCodec[T](implicit
    schema: Schema[T],
  ): HttpContentCodec[ServerSentEvent[T]] = {
    if (schema.isInstanceOf[Schema.Primitive[_]]) contentCodec(HttpContentCodec.text.only[T].defaultCodec, schema)
    else contentCodec(JsonCodec.schemaBasedBinaryCodec, schema)
  }

  def heartbeat: ServerSentEvent[String] = new ServerSentEvent("")
}
