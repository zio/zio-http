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

package zio.http.netty.socket

import zio.http.{SocketDecoder, WebSocketConfig}

import io.netty.handler.codec.http.websocketx.{
  WebSocketClientProtocolConfig,
  WebSocketCloseStatus,
  WebSocketDecoderConfig,
  WebSocketServerProtocolConfig,
}

private[netty] object NettySocketProtocol {

  def clientBuilder(socketProtocol: WebSocketConfig): WebSocketClientProtocolConfig.Builder =
    WebSocketClientProtocolConfig
      .newBuilder()
      .subprotocol(socketProtocol.subprotocols.orNull)
      .handshakeTimeoutMillis(socketProtocol.handshakeTimeoutMillis)
      .forceCloseTimeoutMillis(socketProtocol.forceCloseTimeoutMillis)
      .handleCloseFrames(socketProtocol.handleCloseFrames)
      .sendCloseFrame(closeStatusToNetty(socketProtocol.sendCloseFrame))
      .dropPongFrames(socketProtocol.dropPongFrames)

  def serverBuilder(socketProtocol: WebSocketConfig): WebSocketServerProtocolConfig.Builder =
    WebSocketServerProtocolConfig
      .newBuilder()
      .checkStartsWith(true)
      .websocketPath("")
      .subprotocols(socketProtocol.subprotocols.orNull)
      .handshakeTimeoutMillis(socketProtocol.handshakeTimeoutMillis)
      .forceCloseTimeoutMillis(socketProtocol.forceCloseTimeoutMillis)
      .handleCloseFrames(socketProtocol.handleCloseFrames)
      .sendCloseFrame(closeStatusToNetty(socketProtocol.sendCloseFrame))
      .dropPongFrames(socketProtocol.dropPongFrames)
      .decoderConfig(socketDecoderToNetty(socketProtocol.decoderConfig))

  private def closeStatusToNetty(closeStatus: WebSocketConfig.CloseStatus): WebSocketCloseStatus =
    closeStatus match {
      case WebSocketConfig.CloseStatus.NormalClosure         => WebSocketCloseStatus.NORMAL_CLOSURE
      case WebSocketConfig.CloseStatus.EndpointUnavailable   => WebSocketCloseStatus.ENDPOINT_UNAVAILABLE
      case WebSocketConfig.CloseStatus.ProtocolError         => WebSocketCloseStatus.PROTOCOL_ERROR
      case WebSocketConfig.CloseStatus.InvalidMessageType    => WebSocketCloseStatus.INVALID_MESSAGE_TYPE
      case WebSocketConfig.CloseStatus.InvalidPayloadData    => WebSocketCloseStatus.INVALID_PAYLOAD_DATA
      case WebSocketConfig.CloseStatus.PolicyViolation       => WebSocketCloseStatus.POLICY_VIOLATION
      case WebSocketConfig.CloseStatus.MessageTooBig         => WebSocketCloseStatus.MESSAGE_TOO_BIG
      case WebSocketConfig.CloseStatus.MandatoryExtension    => WebSocketCloseStatus.MANDATORY_EXTENSION
      case WebSocketConfig.CloseStatus.InternalServerError   => WebSocketCloseStatus.INTERNAL_SERVER_ERROR
      case WebSocketConfig.CloseStatus.ServiceRestart        => WebSocketCloseStatus.SERVICE_RESTART
      case WebSocketConfig.CloseStatus.TryAgainLater         => WebSocketCloseStatus.TRY_AGAIN_LATER
      case WebSocketConfig.CloseStatus.BadGateway            => WebSocketCloseStatus.BAD_GATEWAY
      case WebSocketConfig.CloseStatus.Empty                 => WebSocketCloseStatus.EMPTY
      case WebSocketConfig.CloseStatus.AbnormalClosure       => WebSocketCloseStatus.ABNORMAL_CLOSURE
      case WebSocketConfig.CloseStatus.TlsHandshakeFailed    => WebSocketCloseStatus.TLS_HANDSHAKE_FAILED
      case WebSocketConfig.CloseStatus.Custom(code, message) => new WebSocketCloseStatus(code, message)
    }

  private def socketDecoderToNetty(socketDecoder: SocketDecoder): WebSocketDecoderConfig = WebSocketDecoderConfig
    .newBuilder()
    .maxFramePayloadLength(socketDecoder.maxFramePayloadLength)
    .expectMaskedFrames(socketDecoder.expectMaskedFrames)
    .allowMaskMismatch(socketDecoder.allowMaskMismatch)
    .allowExtensions(socketDecoder.allowExtensions)
    .closeOnProtocolViolation(socketDecoder.closeOnProtocolViolation)
    .withUTF8Validator(socketDecoder.withUTF8Validator)
    .build()
}
