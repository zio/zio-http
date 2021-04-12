package zhttp.socket

import io.netty.handler.codec.http.websocketx.{
  WebSocketCloseStatus => JWebSocketCloseStatus,
  WebSocketDecoderConfig => JWebSocketDecoderConfig,
  WebSocketServerProtocolConfig => JWebSocketServerProtocolConfig,
}
import zhttp.socket.Socket.DecoderConfig._
import zhttp.socket.Socket.HandlerConfig._
import zhttp.socket.Socket.ProtocolConfig._
import zhttp.socket.Socket._
import zio.ZIO
import zio.stream.ZStream

case class SocketConfig[-R, +E](
  onTimeout: Option[ZIO[R, Nothing, Unit]] = None,
  onOpen: Option[Connection => ZStream[R, E, WebSocketFrame]] = None,
  onMessage: Option[WebSocketFrame => ZStream[R, E, WebSocketFrame]] = None,
  onError: Option[Throwable => ZIO[R, Nothing, Unit]] = None,
  onClose: Option[Connection => ZIO[R, Nothing, Unit]] = None,
  protocolConfig: JWebSocketServerProtocolConfig = SocketConfig.protocolConfigBuilder.build(),
)

object SocketConfig {

  private def protocolConfigBuilder = JWebSocketServerProtocolConfig
    .newBuilder()
    .checkStartsWith(true)
    .websocketPath("")

  private def decoderConfigBuilder = JWebSocketDecoderConfig
    .newBuilder()

  def fromSocket[R, E](socket: Socket[R, E]): SocketConfig[R, E] = {
    val iSettings: SocketConfig[Any, Nothing] =
      SocketConfig(protocolConfig = protocolConfigBuilder.decoderConfig(decoderConfigBuilder.build()).build())
    val iProtocolConfigBuilder                = protocolConfigBuilder
    val iDecoderConfigBuilder                 = decoderConfigBuilder

    def updateProtocolConfig(config: ProtocolConfig, s: SocketConfig[R, E]): SocketConfig[R, E] = {
      config match {
        case SubProtocol(name)                 => iProtocolConfigBuilder.subprotocols(name)
        case HandshakeTimeoutMillis(duration)  => iProtocolConfigBuilder.handshakeTimeoutMillis(duration.toMillis)
        case ForceCloseTimeoutMillis(duration) => iProtocolConfigBuilder.forceCloseTimeoutMillis(duration.toMillis)
        case ForwardCloseFrames                => iProtocolConfigBuilder.handleCloseFrames(false)
        case SendCloseFrame(status)            => iProtocolConfigBuilder.sendCloseFrame(status.asJava)
        case SendCloseFrameCode(code, reason)  =>
          iProtocolConfigBuilder.sendCloseFrame(new JWebSocketCloseStatus(code, reason))
        case ForwardPongFrames                 => iProtocolConfigBuilder.dropPongFrames(false)
      }
      s
    }

    def updateDecoderConfig(config: DecoderConfig, s: SocketConfig[R, E]): SocketConfig[R, E] = {
      config match {
        case MaxFramePayloadLength(length) => iDecoderConfigBuilder.maxFramePayloadLength(length)
        case RejectMaskedFrames            => iDecoderConfigBuilder.expectMaskedFrames(false)
        case AllowMaskMismatch             => iDecoderConfigBuilder.allowMaskMismatch(true)
        case AllowExtensions               => iDecoderConfigBuilder.allowExtensions(true)
        case AllowProtocolViolation        => iDecoderConfigBuilder.closeOnProtocolViolation(false)
        case SkipUTF8Validation            => iDecoderConfigBuilder.withUTF8Validator(false)
      }
      s
    }

    def updateHandlerConfig(config: HandlerConfig[R, E], s: SocketConfig[R, E]): SocketConfig[R, E] =
      config match {
        case OnTimeout(onTimeout) =>
          s.copy(onTimeout = s.onTimeout.fold(Option(onTimeout))(v => Option(v *> onTimeout)))
        case OnOpen(onOpen)       =>
          s.copy(onOpen = s.onOpen.fold(Option(onOpen))(v => Option((c: Connection) => v(c).merge(onOpen(c)))))
        case OnMessage(onMessage) =>
          s.copy(onMessage = s.onMessage.fold(Option(onMessage))(v => Option(ws => v(ws).merge(onMessage(ws)))))
        case OnError(onError)     => s.copy(onError = s.onError.fold(Option(onError))(v => Option(c => v(c) *> onError(c))))
        case OnClose(onClose)     => s.copy(onClose = s.onClose.fold(Option(onClose))(v => Option(c => v(c) *> onClose(c))))
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
