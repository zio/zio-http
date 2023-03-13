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

import zio.http.socket.{CloseStatus, SocketDecoder, SocketProtocol}

import io.netty.handler.codec.http.websocketx.{
  WebSocketClientProtocolConfig,
  WebSocketCloseStatus,
  WebSocketDecoderConfig,
  WebSocketServerProtocolConfig,
}

private[netty] object NettySocketProtocol {

  def clientBuilder(socketProtocol: SocketProtocol): WebSocketClientProtocolConfig.Builder =
    WebSocketClientProtocolConfig
      .newBuilder()
      .subprotocol(socketProtocol.subprotocols.orNull)
      .handshakeTimeoutMillis(socketProtocol.handshakeTimeoutMillis)
      .forceCloseTimeoutMillis(socketProtocol.forceCloseTimeoutMillis)
      .handleCloseFrames(socketProtocol.handleCloseFrames)
      .sendCloseFrame(closeStatusToNetty(socketProtocol.sendCloseFrame))
      .dropPongFrames(socketProtocol.dropPongFrames)

  def serverBuilder(socketProtocol: SocketProtocol): WebSocketServerProtocolConfig.Builder =
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

  private def closeStatusToNetty(closeStatus: CloseStatus): WebSocketCloseStatus =
    closeStatus match {
      case CloseStatus.NormalClosure         => WebSocketCloseStatus.NORMAL_CLOSURE
      case CloseStatus.EndpointUnavailable   => WebSocketCloseStatus.ENDPOINT_UNAVAILABLE
      case CloseStatus.ProtocolError         => WebSocketCloseStatus.PROTOCOL_ERROR
      case CloseStatus.InvalidMessageType    => WebSocketCloseStatus.INVALID_MESSAGE_TYPE
      case CloseStatus.InvalidPayloadData    => WebSocketCloseStatus.INVALID_PAYLOAD_DATA
      case CloseStatus.PolicyViolation       => WebSocketCloseStatus.POLICY_VIOLATION
      case CloseStatus.MessageTooBig         => WebSocketCloseStatus.MESSAGE_TOO_BIG
      case CloseStatus.MandatoryExtension    => WebSocketCloseStatus.MANDATORY_EXTENSION
      case CloseStatus.InternalServerError   => WebSocketCloseStatus.INTERNAL_SERVER_ERROR
      case CloseStatus.ServiceRestart        => WebSocketCloseStatus.SERVICE_RESTART
      case CloseStatus.TryAgainLater         => WebSocketCloseStatus.TRY_AGAIN_LATER
      case CloseStatus.BadGateway            => WebSocketCloseStatus.BAD_GATEWAY
      case CloseStatus.Empty                 => WebSocketCloseStatus.EMPTY
      case CloseStatus.AbnormalClosure       => WebSocketCloseStatus.ABNORMAL_CLOSURE
      case CloseStatus.TlsHandshakeFailed    => WebSocketCloseStatus.TLS_HANDSHAKE_FAILED
      case CloseStatus.Custom(code, message) => new WebSocketCloseStatus(code, message)
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
