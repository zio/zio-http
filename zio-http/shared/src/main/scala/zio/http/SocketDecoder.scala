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

import zio.stacktracer.TracingImplicits.disableAutoTrace

import com.lihaoyi.unroll

/**
 * The configuration of the WebSocket frame decoder. Use `WebSocketConfig#decoderConfig`
 * to set it.
 *
 * @param maxFramePayloadLength
 *   the maximum length in bytes of a frame's payload. It defaults to 65536.
 * @param expectMaskedFrames
 *   whether incoming frames are expected to be masked. It defaults to `true`.
 *   The client side does not apply this setting.
 * @param allowMaskMismatch
 *   when `true`, frames that are not masked as expected are accepted. It
 *   defaults to `false`.
 * @param allowExtensions
 *   whether the reserved extension bits (RSV) of a frame may be set. It
 *   defaults to `false`.
 * @param closeOnProtocolViolation
 *   whether a close frame is sent immediately when a protocol violation is
 *   detected. It defaults to `true`. The client side does not apply this
 *   setting to the frame decoder, but it does to the aggregation of
 *   fragments.
 * @param withUTF8Validator
 *   whether Netty's UTF-8 validator for text frames is added. It defaults to
 *   `true`.
 * @param aggregateFragments
 *   whether fragmented messages are aggregated into one frame. It defaults to
 *   `true`.
 * @param maxMessageLength
 *   the maximum size in bytes of an aggregated message. It defaults to
 *   1048576.
 */
final case class SocketDecoder(
  maxFramePayloadLength: Int = 65536,
  expectMaskedFrames: Boolean = true,
  allowMaskMismatch: Boolean = false,
  allowExtensions: Boolean = false,
  closeOnProtocolViolation: Boolean = true,
  withUTF8Validator: Boolean = true,
  @unroll
  aggregateFragments: Boolean = true,
  maxMessageLength: Int = 1048576,
) { self =>

  /**
   * Whether fragmented messages (a text or binary frame that is not final,
   * followed by continuation frames) are aggregated into a single, final
   * [[WebSocketFrame.Text]] or [[WebSocketFrame.Binary]] frame before they are
   * passed to the application. When disabled, the individual fragments are
   * passed on as they are received. Note that in that case, the text of a
   * non-final [[WebSocketFrame.Text]] frame may be broken, because a fragment
   * boundary may occur in the middle of a multi-byte UTF-8 sequence.
   */
  def aggregateFragments(enable: Boolean): SocketDecoder = self.copy(aggregateFragments = enable)

  /**
   * The maximum size in bytes of an aggregated message. If a fragmented message
   * exceeds this size and `closeOnProtocolViolation` is enabled, the
   * connection is closed with status 1009 (message too big). Only relevant if
   * `aggregateFragments` is enabled. Unfragmented frames are limited by
   * `maxFramePayloadLength` instead.
   */
  def maxMessageLength(length: Int): SocketDecoder = self.copy(maxMessageLength = length)

  /**
   * Sets `allowExtensions`: whether the reserved extension bits (RSV) of a frame may be set.
   */
  def extensions(allowed: Boolean): SocketDecoder = self.copy(allowExtensions = allowed)

  /**
   * When set to true, frames which are not masked properly according to the
   * standard will still be accepted.
   */
  def maskMismatch(allowed: Boolean): SocketDecoder = self.copy(allowMaskMismatch = allowed)

  /**
   * Sets `expectMaskedFrames`: whether incoming frames are expected to be
   * masked. The frame decoder treats a frame whose masking differs from this as
   * a protocol violation, unless `allowMaskMismatch` is `true`.
   */
  def maskedFrames(allowed: Boolean): SocketDecoder = self.copy(expectMaskedFrames = allowed)

  /**
   * Sets Maximum length of a frame's payload. Setting this to an appropriate
   * value for you application helps check for denial of services attacks.
   */
  def maxFramePayloadLength(length: Int): SocketDecoder = self.copy(maxFramePayloadLength = length)

  /**
   * Sets `closeOnProtocolViolation`: whether a close frame is sent immediately
   * when a protocol violation is detected.
   */
  def protocolViolation(allowed: Boolean): SocketDecoder = self.copy(closeOnProtocolViolation = allowed)

  /**
   * Allows you to avoid adding of Utf8FrameValidator to the pipeline on the
   * WebSocketServerProtocolHandler creation. This is useful (less overhead)
   * when you use only BinaryWebSocketFrame within your web socket connection.
   */
  def uTF8Validation(enable: Boolean): SocketDecoder = self.copy(withUTF8Validator = enable)
}

/** The default decoder configuration. */
object SocketDecoder {

  /**
   * Creates an default decoder configuration.
   */
  def default: SocketDecoder = SocketDecoder()
}
