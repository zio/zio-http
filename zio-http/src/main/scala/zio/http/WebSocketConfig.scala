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

import zio.Duration

/**
 * Server side websocket configuration
 */
final case class WebSocketConfig(
  subprotocols: Option[String] = None,
  handshakeTimeoutMillis: Long = 10000L,
  forceCloseTimeoutMillis: Long = -1L,
  handleCloseFrames: Boolean = true,
  sendCloseFrame: WebSocketConfig.CloseStatus = WebSocketConfig.CloseStatus.NormalClosure,
  dropPongFrames: Boolean = true,
  decoderConfig: SocketDecoder = SocketDecoder.default,
) { self =>

  /**
   * Close frame to send, when close frame was not send manually.
   */
  def closeFrame(code: Int, reason: String): WebSocketConfig =
    self.copy(sendCloseFrame = WebSocketConfig.CloseStatus.Custom(code, reason))

  /**
   * Close frame to send, when close frame was not send manually.
   */
  def closeStatus(status: WebSocketConfig.CloseStatus): WebSocketConfig = self.copy(sendCloseFrame = status)

  def decoderConfig(socketDecoder: SocketDecoder): WebSocketConfig = self.copy(decoderConfig = socketDecoder)

  /**
   * Close the connection if it was not closed by the client after timeout
   * specified
   */
  def forceCloseTimeout(duration: Duration): WebSocketConfig =
    self.copy(forceCloseTimeoutMillis = duration.toMillis)

  /**
   * Close frames should be forwarded
   */
  def forwardCloseFrames(forward: Boolean): WebSocketConfig = self.copy(handleCloseFrames = forward)

  /**
   * Pong frames should be forwarded
   */
  def forwardPongFrames(forward: Boolean): WebSocketConfig = self.copy(dropPongFrames = !forward)

  /**
   * Handshake timeout in mills
   */
  def handshakeTimeout(duration: Duration): WebSocketConfig = self.copy(handshakeTimeoutMillis = duration.toMillis)

  /**
   * Used to specify the websocket sub-protocol
   */
  def subProtocol(name: Option[String]): WebSocketConfig = self.copy(subprotocols = name)
}

object WebSocketConfig {

  /**
   * Creates an default decoder configuration.
   */
  def default: WebSocketConfig = WebSocketConfig()

  sealed trait CloseStatus

  object CloseStatus {
    case object NormalClosure                          extends CloseStatus
    case object EndpointUnavailable                    extends CloseStatus
    case object ProtocolError                          extends CloseStatus
    case object InvalidMessageType                     extends CloseStatus
    case object InvalidPayloadData                     extends CloseStatus
    case object PolicyViolation                        extends CloseStatus
    case object MessageTooBig                          extends CloseStatus
    case object MandatoryExtension                     extends CloseStatus
    case object InternalServerError                    extends CloseStatus
    case object ServiceRestart                         extends CloseStatus
    case object TryAgainLater                          extends CloseStatus
    case object BadGateway                             extends CloseStatus
    case object Empty                                  extends CloseStatus
    case object AbnormalClosure                        extends CloseStatus
    case object TlsHandshakeFailed                     extends CloseStatus
    final case class Custom(code: Int, reason: String) extends CloseStatus
  }
}
