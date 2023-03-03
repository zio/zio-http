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

import io.netty.handler.codec.http.websocketx.WebSocketDecoderConfig
import zio.stacktracer.TracingImplicits.disableAutoTrace // scalafix:ok;

/**
 * Frame decoder configuration
 */
final case class SocketDecoder(
  maxFramePayloadLength: Int = 65536,
  expectMaskedFrames: Boolean = true,
  allowMaskMismatch: Boolean = false,
  allowExtensions: Boolean = false,
  closeOnProtocolViolation: Boolean = true,
  withUTF8Validator: Boolean = true,
) { self =>

  def javaConfig[zio]: WebSocketDecoderConfig = WebSocketDecoderConfig
    .newBuilder()
    .maxFramePayloadLength(maxFramePayloadLength)
    .expectMaskedFrames(expectMaskedFrames)
    .allowMaskMismatch(allowMaskMismatch)
    .allowExtensions(allowExtensions)
    .closeOnProtocolViolation(closeOnProtocolViolation)
    .withUTF8Validator(withUTF8Validator)
    .build()

  def withExtensions(allowed: Boolean): SocketDecoder = self.copy(allowExtensions = allowed)

  /**
   * When set to true, frames which are not masked properly according to the
   * standard will still be accepted.
   */
  def withMaskMismatch(allowed: Boolean): SocketDecoder = self.copy(allowMaskMismatch = allowed)

  /**
   * Web socket servers must set this to true to reject incoming masked payload.
   */
  def withMaskedFrames(allowed: Boolean): SocketDecoder = self.copy(expectMaskedFrames = allowed)

  /**
   * Sets Maximum length of a frame's payload. Setting this to an appropriate
   * value for you application helps check for denial of services attacks.
   */
  def withMaxFramePayloadLength(length: Int): SocketDecoder = self.copy(maxFramePayloadLength = length)

  /**
   * Flag to not send close frame immediately on any protocol violation.ion.
   */
  def withProtocolViolation(allowed: Boolean): SocketDecoder = self.copy(closeOnProtocolViolation = allowed)

  /**
   * Allows you to avoid adding of Utf8FrameValidator to the pipeline on the
   * WebSocketServerProtocolHandler creation. This is useful (less overhead)
   * when you use only BinaryWebSocketFrame within your web socket connection.
   */
  def withUTF8Validation(enable: Boolean): SocketDecoder = self.copy(withUTF8Validator = enable)
}

object SocketDecoder {

  /**
   * Creates an default decoder configuration.
   */
  def default: SocketDecoder = SocketDecoder()
}
