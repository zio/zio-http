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
 * A Server-Sent Event (SSE) as defined by
 * https://html.spec.whatwg.org/multipage/server-sent-events.html#server-sent-events.
 * `T` is the type of the event data.
 *
 * {{{
 * val event: ServerSentEvent[String] = ServerSentEvent("temperature: 21", Some("reading"), Some("7"))
 * val text: String                   = event.encoded
 * // "event: reading\ndata: temperature: 21\nid: 7\n\n"
 * }}}
 *
 * @param data
 *   the event data, which may span multiple lines
 * @param eventType
 *   optional type, must not contain \n or \r
 * @param id
 *   optional id, must not contain \n or \r
 * @param retry
 *   the optional reconnection delay, sent as the `retry` field in milliseconds
 *
 * @see
 *   [[https://zio.dev/zio-http/examples/server-sent-events-in-endpoints/ Server-Sent Events in Endpoints]]
 */
final case class ServerSentEvent[T](
  data: T,
  eventType: Option[String] = None,
  id: Option[String] = None,
  retry: Option[Duration] = None,
) {

  /**
   * Encodes this event as Server-Sent Events text. `String` data is used as it
   * is; any other data is encoded with the implicit codec and read as UTF-8
   * text. See `encodeWith` for the format.
   */
  def encode(implicit binaryCodec: BinaryCodec[T]): String =
    encodeWith {
      case s: String => s
      case data      => binaryCodec.encode(data).asString(Charsets.Utf8)
    }

  /**
   * Encodes this event, whose data is a `String`, as Server-Sent Events text.
   * See `encodeWith` for the format.
   */
  def encoded(implicit ev: T <:< String): String =
    encodeWith(ev)

  /**
   * Encodes this event as Server-Sent Events text, using `f` to turn the data
   * into text.
   *
   * The fields are written in the order `event`, `data`, `id`, `retry`, and a
   * blank line ends the event. Each line of the data text is written as a
   * `data: ` line, and `retry` is written in milliseconds.
   *
   * {{{
   * val event = ServerSentEvent("temperature: 21", Some("reading"), Some("7"))
   * val text: String = event.encodeWith(identity)
   * // "event: reading\ndata: temperature: 21\nid: 7\n\n"
   * }}}
   */
  def encodeWith(f: T => String): String = {
    val dataString: String = f(data)

    val dataLines: Array[String] = dataString.split("\n")
    val isComment                = dataString.startsWith(":")

    val initialCapacity: Int =
      (
        // 6 for "data: ", the data itself, and the newlines
        ((if (isComment) 0 else 6) + dataString.length + dataLines.length)
        // 24 because 7 for "event: ", 1 for the newline, 16 for the event type itself
          + (if (eventType.isEmpty) 0 else 24)
          // 21 because 4 for "id: ", 1 for the newline, 16 for the id itself
          + (if (id.isEmpty) 0 else 21)
          // 24 because 7 for "retry: ", 1 for the newline, 16 for the retry value
          + (if (retry.isEmpty) 0 else 24)
          // for the final newline
          + 1
      )

    val sb = new java.lang.StringBuilder(initialCapacity)
    eventType.foreach { et =>
      sb.append("event: ")
      val iterator = et.linesIterator
      var hasNext  = iterator.hasNext
      while (hasNext) {
        sb.append(iterator.next())
        hasNext = iterator.hasNext
        if (hasNext) sb.append(' ')
      }
      sb.append('\n')
    }
    dataLines.foreach { line =>
      if (isComment) sb.append(line).append('\n')
      else sb.append("data: ").append(line).append('\n')
    }
    id.foreach { i =>
      sb.append("id: ")
      val iterator = i.linesIterator
      var hasNext  = iterator.hasNext
      while (hasNext) {
        sb.append(iterator.next())
        hasNext = iterator.hasNext
        if (hasNext) sb.append(' ')
      }
      sb.append('\n')
    }
    retry.foreach { r =>
      sb.append("retry: ").append(r.toMillis).append('\n')
    }
    sb.append('\n').toString
  }

}

/** Constructors, schemas and codecs for `ServerSentEvent`. */
object ServerSentEvent {

  /**
   * Creates an event whose reconnection delay is given in milliseconds. A
   * negative `retry` gives an event without a reconnection delay.
   *
   * @param data
   *   the event data, which may span multiple lines
   * @param eventType
   *   the optional event type
   * @param id
   *   the optional event id
   * @param retry
   *   the optional reconnection delay in milliseconds
   */
  def apply[T](data: T, eventType: Option[String], id: Option[String], retry: Option[Int])(implicit
    di: DummyImplicit,
  ): ServerSentEvent[T] =
    ServerSentEvent(data, eventType, id, retry.filter(_ >= 0).map(_.milliseconds))

  /** Derives the `Schema` of `ServerSentEvent[T]` from the schema of `T`. */
  implicit def schema[T](implicit schema: Schema[T]): Schema[ServerSentEvent[T]] = DeriveSchema.gen[ServerSentEvent[T]]

  /**
   * The binary codec of the default content codec (`defaultContentCodec`) for
   * `ServerSentEvent[T]`.
   */
  implicit def defaultBinaryCodec[T](implicit schema: Schema[T]): BinaryCodec[ServerSentEvent[T]] =
    defaultContentCodec(schema).defaultCodec

  /**
   * A codec for events whose data is the list of the `data` lines of the event.
   *
   * Decoding reads the fields `data`, `event`, `id` and `retry`. A line `data`
   * without a colon is an empty data line, an empty `event` or `id` value gives
   * `None`, and `retry` is kept only when its value is a non-negative integer
   * number of milliseconds. Other lines, such as comments, are ignored.
   * `decode` of a whole chunk fails with a `DecodeError.EmptyContent` when the
   * event has neither a `data` line nor a `retry` field. The stream decoder
   * emits an event at each blank line, and skips events that have neither.
   *
   * Encoding writes each element of the data as a `data` line.
   */
  val rawEventLines: BinaryCodec[ServerSentEvent[Chunk[String]]] =
    new BinaryCodec[ServerSentEvent[Chunk[String]]] {

      override def decode(whole: Chunk[Byte]): Either[DecodeError, ServerSentEvent[Chunk[String]]] = {
        val event = processEvent(Chunk.fromArray(whole.asString(Charsets.Utf8).split("\n")))
        if (event.data.isEmpty && event.retry.isEmpty)
          Left(DecodeError.EmptyContent("Neither 'data' nor 'retry' fields specified"))
        else
          Right(event)
      }

      override def streamDecoder: ZPipeline[Any, DecodeError, Byte, ServerSentEvent[Chunk[String]]] =
        ZPipeline.utf8Decode.orDie >>>
          ZPipeline.splitOn("\n") >>>
          ZPipeline
            .scan[String, (Boolean, Chunk[String])](false -> Chunk.empty) {
              case ((true, _), "")        => true  -> Chunk.empty
              case ((true, _), line)      => false -> Chunk(line)
              case ((false, lines), "")   => true  -> lines
              case ((false, lines), line) => false -> (lines :+ line)
            }
            .filter { case (completed, event) => completed && event.nonEmpty }
            .map { case (_, lines) => processEvent(lines) }
            .filter(event => event.data.nonEmpty || event.retry.nonEmpty)

      private def processEvent(lines: Chunk[String]): ServerSentEvent[Chunk[String]] =
        lines.foldLeft(ServerSentEvent(data = Chunk.empty[String])) { case (event, line) =>
          val fieldType = "(data|event|id|retry)(:|$)".r.findPrefixOf(line)
          fieldType match {
            case Some("data:")  => event.copy(data = event.data :+ line.replaceFirst("data: ?", ""))
            case Some("data")   => event.copy(data = event.data :+ "")
            case Some("event:") =>
              event.copy(eventType = Some(line.replaceFirst("event: ?", "")).filter(_.nonEmpty))
            case Some("event")  => event.copy(eventType = None)
            case Some("retry:") =>
              import scala.collection.compat._
              event.copy(retry = line.replaceFirst("retry: ?", "").toIntOption.filter(_ >= 0).map(_.milliseconds))
            case Some("retry")  => event.copy(retry = None)
            case Some("id:")    => event.copy(id = Some(line.replaceFirst("id: ?", "")).filter(_.nonEmpty))
            case Some("id")     => event.copy(id = None)
            case _              => event
          }
        }

      override def encode(value: ServerSentEvent[Chunk[String]]): Chunk[Byte] =
        Chunk.fromArray(value.encodeWith(_.mkString("\n")).getBytes(Charsets.Utf8))

      override def streamEncoder: ZPipeline[Any, Nothing, ServerSentEvent[Chunk[String]], Byte] =
        ZPipeline.mapChunks(value => value.flatMap(c => c.encodeWith(_.mkString("\n")).getBytes(Charsets.Utf8)))

    }

  /**
   * A codec for events whose data is a `String`, with the same field handling
   * as `rawEventLines`. When decoding, the `data` lines of an event are
   * concatenated without a line separator.
   */
  val rawEvent: BinaryCodec[ServerSentEvent[String]] =
    new BinaryCodec[ServerSentEvent[String]] {

      override def decode(whole: Chunk[Byte]): Either[DecodeError, ServerSentEvent[String]] =
        rawEventLines.decode(whole).map { e => e.copy(data = e.data.asString) }

      override def streamDecoder: ZPipeline[Any, DecodeError, Byte, ServerSentEvent[String]] =
        rawEventLines.streamDecoder.map { e => e.copy(data = e.data.asString) }

      override def encode(value: ServerSentEvent[String]): Chunk[Byte] =
        Chunk.fromArray(value.encoded.getBytes(Charsets.Utf8))

      override def streamEncoder: ZPipeline[Any, Nothing, ServerSentEvent[String], Byte] =
        ZPipeline.mapChunks(value => value.flatMap(c => c.encoded.getBytes(Charsets.Utf8)))

    }

  /**
   * A codec for `ServerSentEvent[T]` that uses the given codec for the data.
   * When decoding, the `data` lines of an event are joined with a newline
   * character and decoded with that codec; encoding is
   * `ServerSentEvent#encode`. The fields are handled as in `rawEventLines`.
   */
  implicit def binaryCodec[T](implicit binaryCodec: BinaryCodec[T]): BinaryCodec[ServerSentEvent[T]] =
    new BinaryCodec[ServerSentEvent[T]] {

      override def decode(whole: Chunk[Byte]): Either[DecodeError, ServerSentEvent[T]] =
        rawEventLines.decode(whole).flatMap(decodeDataField)

      override def streamDecoder: ZPipeline[Any, DecodeError, Byte, ServerSentEvent[T]] =
        rawEventLines.streamDecoder.mapZIO(event => ZIO.fromEither(decodeDataField(event)))

      private def decodeDataField(event: ServerSentEvent[Chunk[String]]): Either[DecodeError, ServerSentEvent[T]] =
        binaryCodec
          .decode(Chunk.fromArray(event.data.mkString("\n").getBytes(Charsets.Utf8)))
          .map(data => event.copy(data = data))

      override def encode(value: ServerSentEvent[T]): Chunk[Byte] =
        Chunk.fromArray(value.encode.getBytes(Charsets.Utf8))

      override def streamEncoder: ZPipeline[Any, Nothing, ServerSentEvent[T], Byte] =
        ZPipeline.mapChunks(value => value.flatMap(c => c.encode.getBytes(Charsets.Utf8)))

    }

  /**
   * The content codec of `ServerSentEvent[T]` for the media type
   * `text/event-stream`, with the given codec and schema for the data.
   */
  implicit def contentCodec[T](implicit
    codecT: BinaryCodec[T],
    schemaT: Schema[T],
  ): HttpContentCodec[ServerSentEvent[T]] = HttpContentCodec.from(
    MediaType.text.`event-stream` -> BinaryCodecWithSchema(binaryCodec(codecT), schema(schemaT)),
  )

  /**
   * The content codec of `ServerSentEvent[T]` for the media type
   * `text/event-stream`. The data of `T` with a primitive schema is encoded as
   * text, and any other data as JSON.
   */
  implicit def defaultContentCodec[T](implicit
    schema: Schema[T],
  ): HttpContentCodec[ServerSentEvent[T]] = {
    if (schema.isInstanceOf[Schema.Primitive[_]]) contentCodec(HttpContentCodec.text.only[T].defaultCodec, schema)
    else contentCodec(JsonCodec.schemaBasedBinaryCodec, schema)
  }

  /**
   * An event whose data is the comment `:heartbeat`. It is encoded as the
   * comment line `:heartbeat` followed by a blank line.
   */
  val heartbeat: ServerSentEvent[String] = new ServerSentEvent(":heartbeat")
}
