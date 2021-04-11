package zhttp.socket

import zio.stream.ZStream
import zio.ZIO
import zhttp.socket.Socket._
import io.netty.handler.codec.http.websocketx.{
  WebSocketDecoderConfig => JWebSocketDecoderConfig,
  WebSocketServerProtocolConfig => JWebSocketServerProtocolConfig,
}
import zhttp.socket.Socket.DecoderConfig._
import zhttp.socket.Socket.ProtocolConfig._
import zhttp.socket.Socket.HandlerConfig._

case class SocketConfig[-R, +E](
  onOpen: Connection => ZStream[R, E, WebSocketFrame] = (_: Connection) => ZStream.empty,
  onMessage: WebSocketFrame => ZStream[R, E, WebSocketFrame] = (_: WebSocketFrame) => ZStream.empty,
  onError: Throwable => ZIO[R, Nothing, Unit] = (_: Throwable) => ZIO.unit,
  onClose: Connection => ZIO[R, Nothing, Unit] = (_: Connection) => ZIO.unit,
  protocolConfig: JWebSocketServerProtocolConfig = SocketConfig.protocolConfigBuilder.build(),
)

object SocketConfig {

  // TODO: reset defaults to protocol defaults
  private def protocolConfigBuilder = JWebSocketServerProtocolConfig
    .newBuilder()
    .handleCloseFrames(false)
    .dropPongFrames(false)
    .checkStartsWith(true)
    .websocketPath("")

  private def decoderConfigBuilder = JWebSocketDecoderConfig
    .newBuilder()
    .expectMaskedFrames(false)
    .closeOnProtocolViolation(false)
    .withUTF8Validator(false)

  def fromSocket[R, E](socket: Socket[R, E]): SocketConfig[R, E] = {
    val iSettings              =
      SocketConfig(protocolConfig = protocolConfigBuilder.decoderConfig(decoderConfigBuilder.build()).build())
    val iProtocolConfigBuilder = protocolConfigBuilder
    val iDecoderConfigBuilder  = decoderConfigBuilder

    def updateProtocolConfig(config: ProtocolConfig, s: SocketConfig[R, E]): SocketConfig[R, E] = {
      config match {
        case SubProtocol(name)                 => iProtocolConfigBuilder.subprotocols(name)
        case HandshakeTimeoutMillis(duration)  => iProtocolConfigBuilder.handshakeTimeoutMillis(duration.toMillis)
        case ForceCloseTimeoutMillis(duration) => iProtocolConfigBuilder.forceCloseTimeoutMillis(duration.toMillis)
        case HandleCloseFrames                 => iProtocolConfigBuilder.handleCloseFrames(true)
        case SendCloseFrame(status)            => iProtocolConfigBuilder.sendCloseFrame(status.asJava)
        case DropPongFrames                    => iProtocolConfigBuilder.dropPongFrames(true)
      }
      s
    }

    def updateDecoderConfig(config: DecoderConfig, s: SocketConfig[R, E]): SocketConfig[R, E] = {
      config match {
        case DecoderMaxFramePayloadLength(length) => iDecoderConfigBuilder.maxFramePayloadLength(length)
        case ExpectMaskedFrames                   => iDecoderConfigBuilder.expectMaskedFrames(true)
        case AllowMaskMismatch                    => iDecoderConfigBuilder.allowMaskMismatch(true)
        case AllowExtensions                      => iDecoderConfigBuilder.allowExtensions(true)
        case CloseOnProtocolViolation             => iDecoderConfigBuilder.closeOnProtocolViolation(true)
        case WithUTF8Validator                    => iDecoderConfigBuilder.withUTF8Validator(true)
      }
      s
    }

    def updateHandlerConfig(config: HandlerConfig[R, E], s: SocketConfig[R, E]): SocketConfig[R, E] =
      config match {
        case OnOpen(onOpen)       => s.copy(onOpen = onOpen)
        case OnMessage(onMessage) => s.copy(onMessage = ws => s.onMessage(ws).merge(onMessage(ws)))
        case OnError(onError)     => s.copy(onError = onError)
        case OnClose(onClose)     => s.copy(onClose = onClose)
      }

    def loop(ss: Socket[R, E], s: SocketConfig[R, E]): SocketConfig[R, E] = ss match {
      case Concat(a, b)           => loop(b, loop(a, s))
      case c: HandlerConfig[R, E] => updateHandlerConfig(c, s)
      case c: ProtocolConfig      => updateProtocolConfig(c, s)
      case c: DecoderConfig       => updateDecoderConfig(c, s)
    }

    loop(socket, iSettings).copy(protocolConfig =
      iProtocolConfigBuilder.decoderConfig(iDecoderConfigBuilder.build()).build(),
    )
  }
}
