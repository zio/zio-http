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

import zio.Chunk
import zio.stacktracer.TracingImplicits.disableAutoTrace

/**
 * A WebSocket protocol frame (RFC 6455). The subtypes are `Text`, `Binary`, `Continuation`, `Close`, `Ping` and `Pong`.
 *
 * {{{
 * val frame: WebSocketFrame = WebSocketFrame.text("hello")
 * val description: String = frame match {
 *   case WebSocketFrame.Text(text)       => "text of " + text.length + " characters"
 *   case WebSocketFrame.Binary(bytes)    => bytes.length + " bytes"
 *   case WebSocketFrame.Close(status, _) => "close with status " + status
 *   case _                               => "other frame"
 * }
 * // "text of 5 characters"
 * }}}
 *
 * @see [[https://zio.dev/zio-http/reference/socket/websocketframe/ WebSocketFrame]]
 */
sealed trait WebSocketFrame extends Product with Serializable { self =>
  /**
   * Returns `true` when this frame is the last fragment of a message. A frame created with the constructors without an `isFinal` argument, a `Close`, a `Ping` and a `Pong` are final. For a `Text`, `Binary` or `Continuation` frame that the connection received, the value comes from the incoming frame.
   *
   * Equality and `toString` of the frame types do not take `isFinal` into account.
   */
  def isFinal: Boolean = true
}

/** Constructors and extractors for `WebSocketFrame`. */
object WebSocketFrame {

  /** Creates a final `Binary` frame. */
  def binary(bytes: Chunk[Byte]): WebSocketFrame = WebSocketFrame.Binary(bytes)

  /**
   * Creates a `Close` frame with the given status code (RFC 6455, section 7.4) and an optional reason text.
   */
  def close(status: Int, reason: Option[String] = None): WebSocketFrame =
    WebSocketFrame.Close(status, reason)

  /** Creates a final `Continuation` frame. */
  def continuation(buffer: Chunk[Byte]): WebSocketFrame = WebSocketFrame.Continuation(buffer)

  /** Returns the `Ping` frame. */
  def ping: WebSocketFrame = WebSocketFrame.Ping

  /** Returns the `Pong` frame. */
  def pong: WebSocketFrame = WebSocketFrame.Pong

  /** Creates a final `Text` frame. */
  def text(string: String): WebSocketFrame =
    WebSocketFrame.Text(string)

  /** A frame that carries binary data. */
  case class Binary(bytes: Chunk[Byte]) extends WebSocketFrame { override val isFinal: Boolean = true }

  /** A frame that carries text. */
  case class Text(text: String) extends WebSocketFrame { override val isFinal: Boolean = true }

  /**
   * A frame that closes the connection, with a status code (RFC 6455, section 7.4) and an optional reason text.
   */
  final case class Close(status: Int, reason: Option[String]) extends WebSocketFrame

  /**
   * A frame that carries a further fragment of a message that began with a non-final `Text` or `Binary` frame.
   */
  case class Continuation(buffer: Chunk[Byte]) extends WebSocketFrame { override val isFinal: Boolean = true }

  /** Constructors and the extractor for `Binary`. */
  object Binary {
    /** Creates a `Binary` frame with the given `isFinal` flag. */
    def apply(bytes: Chunk[Byte], isFinal: Boolean): Binary        = {
      val arg = isFinal
      new Binary(bytes) { override val isFinal: Boolean = arg }
    }
    /** Extracts the bytes of the frame. */
    def unapply(frame: WebSocketFrame.Binary): Option[Chunk[Byte]] = Some(frame.bytes)
  }

  /** Constructors and the extractor for `Text`. */
  object Text {
    /** Creates a `Text` frame with the given `isFinal` flag. */
    def apply(text: String, isFinal: Boolean): Text         = {
      val arg = isFinal
      new Text(text) { override val isFinal: Boolean = arg }
    }
    /** Extracts the text of the frame. */
    def unapply(frame: WebSocketFrame.Text): Option[String] = Some(frame.text)
  }

  /** A heartbeat frame; the peer answers with a `Pong`. */
  case object Ping extends WebSocketFrame

  /** The answer to a `Ping`, or a one-way heartbeat. */
  case object Pong extends WebSocketFrame

  /** Constructors and the extractor for `Continuation`. */
  object Continuation {
    /** Creates a `Continuation` frame with the given `isFinal` flag. */
    def apply(buffer: Chunk[Byte], isFinal: Boolean): Continuation       = {
      val arg = isFinal
      new Continuation(buffer) { override val isFinal: Boolean = arg }
    }
    /** Extracts the bytes of the frame. */
    def unapply(frame: WebSocketFrame.Continuation): Option[Chunk[Byte]] = Some(frame.buffer)
  }
}
