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

import zio.schema.{DeriveSchema, Schema}

import zio.http._
import zio.http.codec._

/**
 * STOMP Frame as defined by STOMP Protocol Specification 1.2
 * https://stomp.github.io/stomp-specification-1.2.html
 *
 * A STOMP frame consists of:
 *   - Command (mandatory)
 *   - Headers (optional key-value pairs)
 *   - Body (optional)
 *
 * @param command
 *   The STOMP command (e.g., CONNECT, SEND, MESSAGE, etc.)
 * @param headers
 *   Headers as key-value pairs
 * @param body
 *   Optional body content
 */
final case class StompFrame(
  command: StompCommand,
  headers: Map[String, String] = Map.empty,
  body: Option[Chunk[Byte]] = None,
) {

  /**
   * Encodes the frame as bytes: the command name and a newline, one `key:value`
   * line per header, an empty line (these lines are UTF-8 text), the body bytes
   * if there are any, and a terminating NUL byte.
   *
   * In header keys and values a backslash, a carriage return, a newline and a
   * colon are written as `\\`, `\r`, `\n` and `\c`, whatever the command is.
   * Headers are written in the iteration order of the `headers` map.
   */
  def encode: Chunk[Byte] = {
    val builder = new StringBuilder

    // Command line
    builder.append(command.name).append('\n')

    // Headers
    headers.foreach { case (key, value) =>
      builder.append(encodeHeaderKey(key))
      builder.append(':')
      builder.append(encodeHeaderValue(value))
      builder.append('\n')
    }

    // Empty line separating headers from body
    builder.append('\n')

    val headerBytes = Chunk.fromArray(builder.toString.getBytes(Charsets.Utf8))

    // Body (if present) followed by NULL byte
    body match {
      case Some(bodyBytes) =>
        headerBytes ++ bodyBytes ++ Chunk.single(0.toByte)
      case None            =>
        headerBytes ++ Chunk.single(0.toByte)
    }
  }

  /**
   * Escape special characters in header keys according to STOMP 1.2
   */
  private def encodeHeaderKey(key: String): String = {
    key
      .replace("\\", "\\\\")
      .replace("\r", "\\r")
      .replace("\n", "\\n")
      .replace(":", "\\c")
  }

  /**
   * Escape special characters in header values according to STOMP 1.2
   */
  private def encodeHeaderValue(value: String): String = {
    value
      .replace("\\", "\\\\")
      .replace("\r", "\\r")
      .replace("\n", "\\n")
      .replace(":", "\\c")
  }

  /**
   * Returns the value of the header `key`, or `None` when the frame has no
   * header with that key.
   */
  def header(key: String): Option[String] = headers.get(key)

  /**
   * Returns a copy of this frame with the header `key` set to `value`,
   * replacing the value of an existing header with the same key.
   */
  def withHeader(key: String, value: String): StompFrame =
    copy(headers = headers + (key -> value))

  /**
   * Returns a copy of this frame with `bodyBytes` as the body and the
   * `content-length` header set to the number of body bytes, replacing an
   * existing `content-length` header.
   */
  def withBody(bodyBytes: Chunk[Byte]): StompFrame =
    copy(
      body = Some(bodyBytes),
      headers = headers + ("content-length" -> bodyBytes.length.toString),
    )

  /**
   * Returns a copy of this frame with the UTF-8 bytes of `bodyString` as the
   * body and the `content-length` header set to the number of those bytes.
   */
  def withBody(bodyString: String): StompFrame =
    withBody(Chunk.fromArray(bodyString.getBytes(Charsets.Utf8)))
}

/**
 * Decoders, encoders and factory methods for every STOMP command except `STOMP`
 * for `StompFrame`.
 */
object StompFrame {

  /**
   * The `Schema` of `StompFrame`, derived with `DeriveSchema.gen`.
   */
  implicit val schema: Schema[StompFrame] = DeriveSchema.gen[StompFrame]

  /**
   * Parses one frame from `bytes`. The bytes are read as UTF-8 text. The text is
   * cut at the byte offset of the first NUL byte, which is then used as a
   * character offset; with multi-byte characters before the NUL byte, the parsed
   * part runs past it, or `decode` throws `StringIndexOutOfBoundsException`.
   *
   * Returns `Left("Invalid STOMP frame: missing NULL terminator")` when `bytes`
   * has no NUL byte, and `Left("Invalid STOMP command: ...")` when the trimmed
   * first line is not a command known to `StompCommand.fromString`, which
   * ignores case. Header lines without a colon, or with the colon first, are
   * skipped. When a key appears more than once, the first value is kept. Header
   * keys and values are unescaped. The lines after the first empty line, joined
   * with newlines, form the body, which is `None` when they are empty. The body
   * is decoded text encoded again as UTF-8, so bytes that are not valid UTF-8
   * come back as the replacement character (`EF BF BD`). The `content-length`
   * header is not used.
   */
  def decode(bytes: Chunk[Byte]): Either[String, StompFrame] = {
    val content = bytes.asString(Charsets.Utf8)

    // Find the NULL terminator
    val nullIndex = bytes.indexWhere(_ == 0)
    if (nullIndex < 0) {
      return Left("Invalid STOMP frame: missing NULL terminator")
    }

    val frameContent = content.substring(0, nullIndex)
    val lines        = frameContent.split("\n", -1)

    if (lines.isEmpty) {
      return Left("Invalid STOMP frame: empty frame")
    }

    // Parse command
    val commandStr = lines(0).trim
    val command    = StompCommand.fromString(commandStr) match {
      case Some(cmd) => cmd
      case None      => return Left(s"Invalid STOMP command: $commandStr")
    }

    // Parse headers
    var headerIndex = 1
    val headers     = scala.collection.mutable.Map.empty[String, String]

    while (headerIndex < lines.length && lines(headerIndex).nonEmpty) {
      val line       = lines(headerIndex)
      val colonIndex = line.indexOf(':')

      if (colonIndex > 0) {
        val key   = decodeHeaderKey(line.substring(0, colonIndex))
        val value = decodeHeaderValue(line.substring(colonIndex + 1))

        // STOMP 1.2: first header wins in case of duplicates
        if (!headers.contains(key)) {
          headers(key) = value
        }
      }

      headerIndex += 1
    }

    // Parse body (after empty line)
    val bodyStart = headerIndex + 1
    val bodyBytes = if (bodyStart < lines.length) {
      val bodyContent = lines.drop(bodyStart).mkString("\n")
      if (bodyContent.nonEmpty) {
        Some(Chunk.fromArray(bodyContent.getBytes(Charsets.Utf8)))
      } else {
        None
      }
    } else {
      None
    }

    Right(StompFrame(command, headers.toMap, bodyBytes))
  }

  /**
   * Decode special characters in header keys according to STOMP 1.2
   */
  private def decodeHeaderKey(key: String): String = {
    key
      .replace("\\c", ":")
      .replace("\\n", "\n")
      .replace("\\r", "\r")
      .replace("\\\\", "\\")
  }

  /**
   * Decode special characters in header values according to STOMP 1.2
   */
  private def decodeHeaderValue(value: String): String = {
    value
      .replace("\\c", ":")
      .replace("\\n", "\n")
      .replace("\\r", "\r")
      .replace("\\\\", "\\")
  }

  /**
   * A pipeline that splits a byte stream after each NUL byte and decodes every
   * piece with `decode`. Pieces for which `decode` returns a `Left` are dropped,
   * so the error channel never carries a failure; an exception from `decode`
   * ends the stream with a defect. Bytes after the last NUL byte are not
   * emitted.
   *
   * Bytes between frames are not skipped: a newline in front of a command line
   * stays in that piece, and `decode` then rejects it.
   */
  val streamDecoder: ZPipeline[Any, String, Byte, StompFrame] = {
    ZPipeline
      .mapAccum[Byte, Chunk[Byte], Option[Chunk[Byte]]](Chunk.empty) { (buffer, byte) =>
        if (byte == 0.toByte) {
          // Found NULL terminator - emit the accumulated buffer with NULL
          val frame = buffer :+ byte
          (Chunk.empty, Some(frame))
        } else {
          // Accumulate byte
          (buffer :+ byte, None)
        }
      }
      .collectSome
      .map { frameBytes =>
        decode(frameBytes)
      }
      .collectRight
  }

  /**
   * A pipeline that writes each frame with `encode` and concatenates the bytes.
   */
  val streamEncoder: ZPipeline[Any, Nothing, StompFrame, Byte] =
    ZPipeline.mapChunks(frames => frames.flatMap(_.encode))

  // Factory methods for common frames

  /**
   * Returns a `CONNECT` frame with the headers `accept-version` (the value of
   * `acceptVersion`, `1.2` by default) and `host`. It adds `login` and
   * `passcode` when given, and `heart-beat` written as `cx,cy` from the two
   * numbers of `heartBeat` when given.
   */
  def connect(
    host: String,
    login: Option[String] = None,
    passcode: Option[String] = None,
    acceptVersion: String = "1.2",
    heartBeat: Option[(Int, Int)] = None,
  ): StompFrame = {
    var headers = Map(
      "accept-version" -> acceptVersion,
      "host"           -> host,
    )

    login.foreach(l => headers += ("login" -> l))
    passcode.foreach(p => headers += ("passcode" -> p))
    heartBeat.foreach { case (cx, cy) =>
      headers += ("heart-beat" -> s"$cx,$cy")
    }

    StompFrame(StompCommand.Connect, headers)
  }

  /**
   * Returns a `CONNECTED` frame with the header `version` (`1.2` by default).
   * It adds `session` and `server` when given, and `heart-beat` written as
   * `sx,sy` from the two numbers of `heartBeat` when given.
   */
  def connected(
    version: String = "1.2",
    session: Option[String] = None,
    server: Option[String] = None,
    heartBeat: Option[(Int, Int)] = None,
  ): StompFrame = {
    var headers = Map("version" -> version)

    session.foreach(s => headers += ("session" -> s))
    server.foreach(s => headers += ("server" -> s))
    heartBeat.foreach { case (sx, sy) =>
      headers += ("heart-beat" -> s"$sx,$sy")
    }

    StompFrame(StompCommand.Connected, headers)
  }

  /**
   * Returns a `SEND` frame with the header `destination` and `body` as the
   * body. `additionalHeaders` are added after `destination`, so a key in both
   * takes the value from `additionalHeaders`. `contentType`, when given, is
   * written as `content-type` and replaces an entry with that key in
   * `additionalHeaders`. The `content-length` header is set to the length of
   * `body` and replaces an entry with that key in `additionalHeaders`.
   */
  def send(
    destination: String,
    body: Chunk[Byte],
    contentType: Option[String] = None,
    additionalHeaders: Map[String, String] = Map.empty,
  ): StompFrame = {
    var headers = Map("destination" -> destination) ++ additionalHeaders
    contentType.foreach(ct => headers += ("content-type" -> ct))

    StompFrame(StompCommand.Send, headers).withBody(body)
  }

  /**
   * Returns the same frame as the `Chunk[Byte]` overload, with `body` encoded
   * as UTF-8. This overload has no default arguments, so `contentType` and
   * `additionalHeaders` are always passed.
   */
  def send(
    destination: String,
    body: String,
    contentType: Option[String],
    additionalHeaders: Map[String, String],
  )(implicit di: DummyImplicit): StompFrame =
    send(destination, Chunk.fromArray(body.getBytes(Charsets.Utf8)), contentType, additionalHeaders)

  /**
   * Returns a `SUBSCRIBE` frame with the headers `destination`, `id` and `ack`
   * (`auto` by default). `additionalHeaders` are added after them, so a key in
   * both takes the value from `additionalHeaders`.
   */
  def subscribe(
    destination: String,
    id: String,
    ack: String = "auto",
    additionalHeaders: Map[String, String] = Map.empty,
  ): StompFrame = {
    val headers = Map(
      "destination" -> destination,
      "id"          -> id,
      "ack"         -> ack,
    ) ++ additionalHeaders

    StompFrame(StompCommand.Subscribe, headers)
  }

  /**
   * Returns an `UNSUBSCRIBE` frame with the header `id`.
   */
  def unsubscribe(id: String): StompFrame =
    StompFrame(StompCommand.Unsubscribe, Map("id" -> id))

  /**
   * Returns an `ACK` frame with the header `id`, and the header `transaction`
   * when given.
   */
  def ack(
    id: String,
    transaction: Option[String] = None,
  ): StompFrame = {
    var headers = Map("id" -> id)
    transaction.foreach(t => headers += ("transaction" -> t))

    StompFrame(StompCommand.Ack, headers)
  }

  /**
   * Returns a `NACK` frame with the header `id`, and the header `transaction`
   * when given.
   */
  def nack(
    id: String,
    transaction: Option[String] = None,
  ): StompFrame = {
    var headers = Map("id" -> id)
    transaction.foreach(t => headers += ("transaction" -> t))

    StompFrame(StompCommand.Nack, headers)
  }

  /**
   * Returns a `BEGIN` frame with the header `transaction`.
   */
  def begin(transaction: String): StompFrame =
    StompFrame(StompCommand.Begin, Map("transaction" -> transaction))

  /**
   * Returns a `COMMIT` frame with the header `transaction`.
   */
  def commit(transaction: String): StompFrame =
    StompFrame(StompCommand.Commit, Map("transaction" -> transaction))

  /**
   * Returns an `ABORT` frame with the header `transaction`.
   */
  def abort(transaction: String): StompFrame =
    StompFrame(StompCommand.Abort, Map("transaction" -> transaction))

  /**
   * Returns a `DISCONNECT` frame with the header `receipt` when `receipt` is
   * given, and no headers otherwise.
   */
  def disconnect(receipt: Option[String] = None): StompFrame = {
    val headers = receipt.map(r => Map("receipt" -> r)).getOrElse(Map.empty)
    StompFrame(StompCommand.Disconnect, headers)
  }

  /**
   * Returns a `MESSAGE` frame with the headers `destination`, `message-id` and
   * `subscription` and `body` as the body. `additionalHeaders` are added after
   * those three, so a key in both takes the value from `additionalHeaders`.
   * `contentType`, when given, is written as `content-type` and replaces an
   * entry with that key in `additionalHeaders`. The `content-length` header is
   * set to the length of `body` and replaces an entry with that key in
   * `additionalHeaders`.
   */
  def message(
    destination: String,
    messageId: String,
    subscription: String,
    body: Chunk[Byte],
    contentType: Option[String] = None,
    additionalHeaders: Map[String, String] = Map.empty,
  ): StompFrame = {
    var headers = Map(
      "destination"  -> destination,
      "message-id"   -> messageId,
      "subscription" -> subscription,
    ) ++ additionalHeaders

    contentType.foreach(ct => headers += ("content-type" -> ct))

    StompFrame(StompCommand.Message, headers).withBody(body)
  }

  /**
   * Returns a `RECEIPT` frame with the header `receipt-id`.
   */
  def receipt(receiptId: String): StompFrame =
    StompFrame(StompCommand.Receipt, Map("receipt-id" -> receiptId))

  /**
   * Returns an `ERROR` frame with the headers `message` and `receipt-id` when
   * given. When `body` is given, its UTF-8 bytes are the body and the
   * `content-length` header is set to their number.
   */
  def error(
    message: Option[String] = None,
    body: Option[String] = None,
    receiptId: Option[String] = None,
  ): StompFrame = {
    var headers = Map.empty[String, String]
    message.foreach(m => headers += ("message" -> m))
    receiptId.foreach(r => headers += ("receipt-id" -> r))

    val frame = StompFrame(StompCommand.Error, headers)
    body.map(b => frame.withBody(b)).getOrElse(frame)
  }

  /**
   * Returns a frame with the `Heartbeat` command, no headers and no body. Its
   * `encode` output is three newline bytes followed by a NUL byte, and `decode`
   * returns `Left("Invalid STOMP command: ")` for that output.
   */
  def heartbeat: StompFrame =
    StompFrame(StompCommand.Heartbeat, Map.empty, None)
}
