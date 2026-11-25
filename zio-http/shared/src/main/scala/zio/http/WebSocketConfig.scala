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
import zio.stacktracer.TracingImplicits.disableAutoTrace

/**
 * The settings of a WebSocket connection, on the server and on the client. A
 * `WebSocketApp` can carry its own config (`WebSocketApp#withConfig`);
 * otherwise the server's `Server.Config#webSocketConfig` or the client's
 * `ZClient.Config#webSocketConfig` applies.
 *
 * {{{
 * import zio._
 *
 * val config: WebSocketConfig = WebSocketConfig.default.subProtocol(Some("chat")).handshakeTimeout(5.seconds)
 * val timeout: Long           = config.handshakeTimeoutMillis // 5000
 * }}}
 *
 * @param subprotocols
 *   the WebSocket sub-protocol name; with `None`, no sub-protocol is set
 * @param handshakeTimeoutMillis
 *   the time in milliseconds allowed for the handshake to complete. It defaults
 *   to 10000.
 * @param forceCloseTimeoutMillis
 *   passed to Netty as the force close timeout, in milliseconds. The default,
 *   -1, sets no timeout.
 * @param handleCloseFrames
 *   when `true`, the default, Netty handles the close frames and does not pass
 *   them to the application; when `false`, they are passed on
 * @param sendCloseFrame
 *   the close frame to send when the connection is closed and no close frame
 *   was sent
 * @param dropPongFrames
 *   when `true`, the default, Netty drops the pong frames; when `false`, they
 *   are passed to the application
 * @param decoderConfig
 *   the configuration of the frame decoder
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
   * Close frame to send, when close frame was not sent manually.
   */
  def closeFrame(code: Int, reason: String): WebSocketConfig =
    self.copy(sendCloseFrame = WebSocketConfig.CloseStatus.Custom(code, reason))

  /**
   * Close frame to send, when close frame was not sent manually.
   */
  def closeStatus(status: WebSocketConfig.CloseStatus): WebSocketConfig = self.copy(sendCloseFrame = status)

  /** Returns a copy with the given frame decoder configuration. */
  def decoderConfig(socketDecoder: SocketDecoder): WebSocketConfig = self.copy(decoderConfig = socketDecoder)

  /**
   * Sets `forceCloseTimeoutMillis` to the given duration in whole milliseconds.
   */
  def forceCloseTimeout(duration: Duration): WebSocketConfig =
    self.copy(forceCloseTimeoutMillis = duration.toMillis)

  /**
   * When `forward` is `true`, close frames are passed to the application
   * instead of being handled by Netty alone. This sets `handleCloseFrames` to
   * the opposite of `forward`.
   */
  def forwardCloseFrames(forward: Boolean): WebSocketConfig = self.copy(handleCloseFrames = !forward)

  /**
   * When `forward` is `true`, pong frames are passed to the application. This
   * sets `dropPongFrames` to the opposite of `forward`.
   */
  def forwardPongFrames(forward: Boolean): WebSocketConfig = self.copy(dropPongFrames = !forward)

  /**
   * Sets `handshakeTimeoutMillis` to the given duration in whole milliseconds.
   */
  def handshakeTimeout(duration: Duration): WebSocketConfig = self.copy(handshakeTimeoutMillis = duration.toMillis)

  /**
   * Sets the WebSocket sub-protocol name, or removes it with `None`.
   */
  def subProtocol(name: Option[String]): WebSocketConfig = self.copy(subprotocols = name)
}

/** The default configuration, and the types of its fields. */
object WebSocketConfig {

  /**
   * The default configuration, `WebSocketConfig()`.
   */
  def default: WebSocketConfig = WebSocketConfig()

  /**
   * The status code of the close frame that is sent when a connection is closed
   * without a close frame having been sent (see
   * `WebSocketConfig#sendCloseFrame`). The status objects map to the Netty
   * `WebSocketCloseStatus` constants of the same meaning; `Custom` carries any
   * code and reason.
   */
  sealed trait CloseStatus

  /** The close statuses. */
  object CloseStatus {

    /** Status 1000, normal closure. */
    case object NormalClosure extends CloseStatus

    /** Status 1001, the endpoint is going away. */
    case object EndpointUnavailable extends CloseStatus

    /** Status 1002, protocol error. */
    case object ProtocolError extends CloseStatus

    /** Status 1003, invalid message type. */
    case object InvalidMessageType extends CloseStatus

    /** Status 1007, invalid payload data. */
    case object InvalidPayloadData extends CloseStatus

    /** Status 1008, policy violation. */
    case object PolicyViolation extends CloseStatus

    /** Status 1009, message too big. */
    case object MessageTooBig extends CloseStatus

    /** Status 1010, mandatory extension. */
    case object MandatoryExtension extends CloseStatus

    /** Status 1011, internal server error. */
    case object InternalServerError extends CloseStatus

    /** Status 1012, service restart. */
    case object ServiceRestart extends CloseStatus

    /** Status 1013, try again later. */
    case object TryAgainLater extends CloseStatus

    /** Status 1014, bad gateway. */
    case object BadGateway extends CloseStatus

    /** Status 1005, no status code present. */
    case object Empty extends CloseStatus

    /** Status 1006, abnormal closure. */
    case object AbnormalClosure extends CloseStatus

    /** Status 1015, TLS handshake failed. */
    case object TlsHandshakeFailed extends CloseStatus

    /** A status with the given `code` and `reason`. */
    final case class Custom(code: Int, reason: String) extends CloseStatus
  }
}
