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

package zio.http.socket

import zio.Chunk
import zio.stacktracer.TracingImplicits.disableAutoTrace

sealed trait WebSocketFrame extends Product with Serializable { self =>
  def isFinal: Boolean = true
}

object WebSocketFrame {

  def binary(bytes: Chunk[Byte]): WebSocketFrame = WebSocketFrame.Binary(bytes)

  def close(status: Int, reason: Option[String] = None): WebSocketFrame =
    WebSocketFrame.Close(status, reason)

  def continuation(buffer: Chunk[Byte]): WebSocketFrame = WebSocketFrame.Continuation(buffer)

  def ping: WebSocketFrame = WebSocketFrame.Ping

  def pong: WebSocketFrame = WebSocketFrame.Pong

  def text(string: String): WebSocketFrame =
    WebSocketFrame.Text(string)

  case class Binary(bytes: Chunk[Byte]) extends WebSocketFrame { override val isFinal: Boolean = true }

  case class Text(text: String) extends WebSocketFrame { override val isFinal: Boolean = true }

  final case class Close(status: Int, reason: Option[String]) extends WebSocketFrame

  case class Continuation(buffer: Chunk[Byte]) extends WebSocketFrame { override val isFinal: Boolean = true }

  object Binary {
    def apply(bytes: Chunk[Byte], isFinal: Boolean): Binary        = {
      val arg = isFinal
      new Binary(bytes) { override val isFinal: Boolean = arg }
    }
    def unapply(frame: WebSocketFrame.Binary): Option[Chunk[Byte]] = Some(frame.bytes)
  }

  object Text {
    def apply(text: String, isFinal: Boolean): Text         = {
      val arg = isFinal
      new Text(text) { override val isFinal: Boolean = arg }
    }
    def unapply(frame: WebSocketFrame.Text): Option[String] = Some(frame.text)
  }

  case object Ping extends WebSocketFrame

  case object Pong extends WebSocketFrame

  object Continuation {
    def apply(buffer: Chunk[Byte], isFinal: Boolean): Continuation       = {
      val arg = isFinal
      new Continuation(buffer) { override val isFinal: Boolean = arg }
    }
    def unapply(frame: WebSocketFrame.Continuation): Option[Chunk[Byte]] = Some(frame.buffer)
  }
}
