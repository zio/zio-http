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

import zio.Duration

/**
 * Server side websocket configuration
 */
final case class SocketProtocol(
  subprotocols: Option[String] = None,
  handshakeTimeoutMillis: Long = 10000L,
  forceCloseTimeoutMillis: Long = -1L,
  handleCloseFrames: Boolean = true,
  sendCloseFrame: CloseStatus = CloseStatus.NormalClosure,
  dropPongFrames: Boolean = true,
  decoderConfig: SocketDecoder = SocketDecoder.default,
) { self =>

  /**
   * Close frame to send, when close frame was not send manually.
   */
  def withCloseFrame(code: Int, reason: String): SocketProtocol =
    self.copy(sendCloseFrame = CloseStatus.Custom(code, reason))

  /**
   * Close frame to send, when close frame was not send manually.
   */
  def withCloseStatus(status: CloseStatus): SocketProtocol = self.copy(sendCloseFrame = status)

  def withDecoderConfig(socketDecoder: SocketDecoder): SocketProtocol = self.copy(decoderConfig = socketDecoder)

  /**
   * Close the connection if it was not closed by the client after timeout
   * specified
   */
  def withForceCloseTimeout(duration: Duration): SocketProtocol = self.copy(forceCloseTimeoutMillis = duration.toMillis)

  /**
   * Close frames should be forwarded
   */
  def withForwardCloseFrames(forward: Boolean): SocketProtocol = self.copy(handleCloseFrames = forward)

  /**
   * Pong frames should be forwarded
   */
  def withForwardPongFrames(forward: Boolean): SocketProtocol = self.copy(dropPongFrames = !forward)

  /**
   * Handshake timeout in mills
   */
  def withHandshakeTimeout(duration: Duration): SocketProtocol = self.copy(handshakeTimeoutMillis = duration.toMillis)

  /**
   * Used to specify the websocket sub-protocol
   */
  def withSubProtocol(name: Option[String]): SocketProtocol = self.copy(subprotocols = name)
}

object SocketProtocol {

  /**
   * Creates an default decoder configuration.
   */
  def default: SocketProtocol = SocketProtocol()
}
