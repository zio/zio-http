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

import zio.stacktracer.TracingImplicits.disableAutoTrace

import zio.http.netty.Names
import zio.http.{SocketDecoder, WebSocketConfig}

import io.netty.channel.{ChannelFutureListener, ChannelHandlerContext, ChannelPipeline}
import io.netty.handler.codec.http.websocketx._
private[netty] object NettySocketProtocol {

  /**
   * Adds a handler to the pipeline that aggregates fragmented messages into a
   * single frame, if enabled in the given config. Must be added after the
   * WebSocket protocol handler and before the WebSocket application handler.
   */
  def addFrameAggregator(pipeline: ChannelPipeline, webSocketConfig: WebSocketConfig): ChannelPipeline = {
    val decoderConfig = webSocketConfig.decoderConfig
    if (decoderConfig.aggregateFragments)
      pipeline.addLast(
        Names.WebSocketFrameAggregator,
        new FrameAggregator(decoderConfig.maxMessageLength, decoderConfig.closeOnProtocolViolation),
      )
    else pipeline
  }

  /**
   * Netty's aggregator only fires a `TooLongFrameException` for oversized
   * messages and keeps the connection open. Like Netty's frame decoder does for
   * oversized frames, we additionally close the connection with status 1009
   * (message too big) if `closeOnProtocolViolation` is enabled.
   */
  private final class FrameAggregator(maxMessageLength: Int, closeOnProtocolViolation: Boolean)
      extends WebSocketFrameAggregator(maxMessageLength) {
    override protected def handleOversizedMessage(ctx: ChannelHandlerContext, oversized: WebSocketFrame): Unit = {
      if (closeOnProtocolViolation && ctx.channel().isActive) {
        val _ = ctx
          .writeAndFlush(new CloseWebSocketFrame(WebSocketCloseStatus.MESSAGE_TOO_BIG))
          .addListener(ChannelFutureListener.CLOSE)
      }
      super.handleOversizedMessage(ctx, oversized)
    }
  }

  def clientBuilder(webSocketConfig: WebSocketConfig): WebSocketClientProtocolConfig.Builder = {
    import webSocketConfig.decoderConfig
    WebSocketClientProtocolConfig
      .newBuilder()
      .subprotocol(webSocketConfig.subprotocols.orNull)
      .handshakeTimeoutMillis(webSocketConfig.handshakeTimeoutMillis)
      .forceCloseTimeoutMillis(webSocketConfig.forceCloseTimeoutMillis)
      .handleCloseFrames(webSocketConfig.handleCloseFrames)
      .sendCloseFrame(closeStatusToNetty(webSocketConfig.sendCloseFrame))
      .dropPongFrames(webSocketConfig.dropPongFrames)
      .maxFramePayloadLength(decoderConfig.maxFramePayloadLength)
      .allowMaskMismatch(decoderConfig.allowMaskMismatch)
      .allowExtensions(decoderConfig.allowExtensions)
      .withUTF8Validator(decoderConfig.withUTF8Validator)
  }

  def serverBuilder(webSocketConfig: WebSocketConfig): WebSocketServerProtocolConfig.Builder =
    WebSocketServerProtocolConfig
      .newBuilder()
      .checkStartsWith(true)
      .websocketPath("")
      .subprotocols(webSocketConfig.subprotocols.orNull)
      .handshakeTimeoutMillis(webSocketConfig.handshakeTimeoutMillis)
      .forceCloseTimeoutMillis(webSocketConfig.forceCloseTimeoutMillis)
      .handleCloseFrames(webSocketConfig.handleCloseFrames)
      .sendCloseFrame(closeStatusToNetty(webSocketConfig.sendCloseFrame))
      .dropPongFrames(webSocketConfig.dropPongFrames)
      .decoderConfig(socketDecoderToNetty(webSocketConfig.decoderConfig))

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
