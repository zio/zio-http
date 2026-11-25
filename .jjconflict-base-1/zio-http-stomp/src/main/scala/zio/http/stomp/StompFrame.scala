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
   * Encode the STOMP frame to bytes according to STOMP 1.2 specification
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
   * Get a header value by key
   */
  def header(key: String): Option[String] = headers.get(key)

  /**
   * Add or update a header
   */
  def withHeader(key: String, value: String): StompFrame =
    copy(headers = headers + (key -> value))

  /**
   * Set the body
   */
  def withBody(bodyBytes: Chunk[Byte]): StompFrame =
    copy(
      body = Some(bodyBytes),
      headers = headers + ("content-length" -> bodyBytes.length.toString),
    )

  /**
   * Set the body from a string
   */
  def withBody(bodyString: String): StompFrame =
    withBody(Chunk.fromArray(bodyString.getBytes(Charsets.Utf8)))
}

object StompFrame {

  implicit val schema: Schema[StompFrame] = DeriveSchema.gen[StompFrame]

  /**
   * Parse a STOMP frame from bytes
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
   * Stream decoder for STOMP frames
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
   * Stream encoder for STOMP frames
   */
  val streamEncoder: ZPipeline[Any, Nothing, StompFrame, Byte] =
    ZPipeline.mapChunks(frames => frames.flatMap(_.encode))

  // Factory methods for common frames

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

  def send(
    destination: String,
    body: String,
    contentType: Option[String],
    additionalHeaders: Map[String, String],
  )(implicit di: DummyImplicit): StompFrame =
    send(destination, Chunk.fromArray(body.getBytes(Charsets.Utf8)), contentType, additionalHeaders)

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

  def unsubscribe(id: String): StompFrame =
    StompFrame(StompCommand.Unsubscribe, Map("id" -> id))

  def ack(
    id: String,
    transaction: Option[String] = None,
  ): StompFrame = {
    var headers = Map("id" -> id)
    transaction.foreach(t => headers += ("transaction" -> t))

    StompFrame(StompCommand.Ack, headers)
  }

  def nack(
    id: String,
    transaction: Option[String] = None,
  ): StompFrame = {
    var headers = Map("id" -> id)
    transaction.foreach(t => headers += ("transaction" -> t))

    StompFrame(StompCommand.Nack, headers)
  }

  def begin(transaction: String): StompFrame =
    StompFrame(StompCommand.Begin, Map("transaction" -> transaction))

  def commit(transaction: String): StompFrame =
    StompFrame(StompCommand.Commit, Map("transaction" -> transaction))

  def abort(transaction: String): StompFrame =
    StompFrame(StompCommand.Abort, Map("transaction" -> transaction))

  def disconnect(receipt: Option[String] = None): StompFrame = {
    val headers = receipt.map(r => Map("receipt" -> r)).getOrElse(Map.empty)
    StompFrame(StompCommand.Disconnect, headers)
  }

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

  def receipt(receiptId: String): StompFrame =
    StompFrame(StompCommand.Receipt, Map("receipt-id" -> receiptId))

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

  def heartbeat: StompFrame =
    StompFrame(StompCommand.Heartbeat, Map.empty, None)
}
