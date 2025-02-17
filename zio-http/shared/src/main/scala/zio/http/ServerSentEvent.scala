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
import zio.http.codec._
import zio.schema.{DeriveSchema, Schema}
import zio.schema.codec._
import zio.stream.ZPipeline

import java.nio.charset.StandardCharsets

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
  retry: Option[Duration] = None,
) {

  def encode(implicit binaryCodec: BinaryCodec[T]): String = {
    val sb = new StringBuilder
    binaryCodec.encode(data).asString(StandardCharsets.UTF_8).linesIterator.foreach { line =>
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

  implicit def binaryCodec[T](implicit binaryCodec: BinaryCodec[T]): BinaryCodec[ServerSentEvent[T]] =
    new BinaryCodec[ServerSentEvent[T]] {
      override def decode(whole: Chunk[Byte]): Either[DecodeError, ServerSentEvent[T]] = {
        val event = processEvent(Chunk.fromArray(whole.asString(StandardCharsets.UTF_8).split("\n")))
        if (event.data.isEmpty && event.retry.isEmpty)
          Left(DecodeError.EmptyContent("Neither 'data' nor 'retry' fields specified"))
        else
          decodeDataField(event)
      }

      override def streamDecoder: ZPipeline[Any, DecodeError, Byte, ServerSentEvent[T]] =
        ZPipeline.utf8Decode.orDie >>>
          ZPipeline.splitOn("\n") >>>
          ZPipeline
            .scan[String, (Boolean, Chunk[String])](false -> Chunk.empty) {
              case ((true, _), "")        => true  -> Chunk.empty
              case ((true, _), line)      => false -> Chunk(line)
              case ((false, lines), "")   => true  -> lines
              case ((false, lines), line) => false -> lines.appended(line)
            }
            .filter { case (completed, event) => completed && event.nonEmpty }
            .map { case (_, lines) => processEvent(lines) }
            .filter(event => event.data.nonEmpty || event.retry.nonEmpty)
            .mapZIO(event => ZIO.fromEither(decodeDataField(event)))

      private def processEvent(lines: Chunk[String]): ServerSentEvent[Chunk[String]] =
        lines.foldLeft(ServerSentEvent(data = Chunk.empty[String])) { case (event, line) =>
          val fieldType = "(data|event|id|retry)(:|$)".r.findPrefixOf(line)
          fieldType match {
            case Some("data:")  => event.copy(data = event.data.appended(line.replaceFirst("data: ?", "")))
            case Some("data")   => event.copy(data = event.data.appended(""))
            case Some("event:") =>
              event.copy(eventType = Some(line.replaceFirst("event: ?", "")).filter(_.nonEmpty))
            case Some("event")  => event.copy(eventType = None)
            case Some("retry:") =>
              event.copy(retry =
                Some(line.replaceFirst("retry: ?", ""))
                  .filter(_.nonEmpty)
                  .flatMap(_.toIntOption)
                  .map(_.milliseconds),
              )
            case Some("retry")  => event.copy(retry = None)
            case Some("id:")    => event.copy(id = Some(line.replaceFirst("id: ?", "")).filter(_.nonEmpty))
            case Some("id")     => event.copy(id = None)
            case _              => event
          }
        }

      private def decodeDataField(event: ServerSentEvent[Chunk[String]]): Either[DecodeError, ServerSentEvent[T]] =
        binaryCodec
          .decode(Chunk.fromArray(event.data.mkString("\n").getBytes(StandardCharsets.UTF_8)))
          .map(data => event.copy(data = data))

      override def encode(value: ServerSentEvent[T]): Chunk[Byte] =
        Chunk.fromArray(value.encode.getBytes(StandardCharsets.UTF_8))

      override def streamEncoder: ZPipeline[Any, Nothing, ServerSentEvent[T], Byte] =
        ZPipeline.mapChunks(value => value.flatMap(c => c.encode.getBytes(StandardCharsets.UTF_8)))
    }

  implicit def contentCodec[T](implicit
    codecT: BinaryCodec[T],
    schemaT: Schema[T],
  ): HttpContentCodec[ServerSentEvent[T]] = HttpContentCodec.from(
    MediaType.text.`event-stream` -> BinaryCodecWithSchema(binaryCodec(codecT), schema(schemaT)),
  )

  implicit def defaultContentCodec[T](implicit
    schema: Schema[T],
  ): HttpContentCodec[ServerSentEvent[T]] = {
    if (schema.isInstanceOf[Schema.Primitive[_]]) contentCodec(HttpContentCodec.text.only[T].defaultCodec, schema)
    else contentCodec(JsonCodec.schemaBasedBinaryCodec, schema)
  }

  def heartbeat: ServerSentEvent[String] = new ServerSentEvent("")
}
