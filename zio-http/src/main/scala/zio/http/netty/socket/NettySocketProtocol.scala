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
