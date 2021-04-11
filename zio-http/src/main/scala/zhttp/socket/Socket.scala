package zhttp.socket

import io.netty.handler.codec.http.websocketx.{
  WebSocketDecoderConfig => JWebSocketDecoderConfig,
  WebSocketServerProtocolConfig => JWebSocketServerProtocolConfig,
}
import zio._
import zio.duration.Duration
import zio.stream.ZStream

import java.net.{SocketAddress => JSocketAddress}

sealed trait Socket[-R, +E] { self =>
  def <+>[R1 <: R, E1 >: E](other: Socket[R1, E1]): Socket[R1, E1] = Socket.Concat(self, other)
  def settings: Socket.Settings[R, E]                              = Socket.settings(self)
}

object Socket {
  type Connection = JSocketAddress
  type Cause      = Option[Throwable]

  case class Settings[-R, +E](
    onOpen: Connection => ZStream[R, E, WebSocketFrame] = (_: Connection) => ZStream.empty,
    onMessage: WebSocketFrame => ZStream[R, E, WebSocketFrame] = (_: WebSocketFrame) => ZStream.empty,
    onError: Throwable => ZIO[R, Nothing, Unit] = (_: Throwable) => ZIO.unit,
    onClose: Connection => ZIO[R, Nothing, Unit] = (_: Connection) => ZIO.unit,
    config: JWebSocketServerProtocolConfig.Builder,
  )

  case class OnOpen[R, E](onOpen: Connection => ZStream[R, E, WebSocketFrame])           extends Socket[R, E]
  case class OnMessage[R, E](onMessage: WebSocketFrame => ZStream[R, E, WebSocketFrame]) extends Socket[R, E]
  case class OnError[R](onError: Throwable => ZIO[R, Nothing, Unit])                     extends Socket[R, Nothing]
  case class OnClose[R](onClose: Connection => ZIO[R, Nothing, Unit])                    extends Socket[R, Nothing]
  case class Concat[R, E](a: Socket[R, E], b: Socket[R, E])                              extends Socket[R, E]

  sealed trait ProtocolConfig extends Socket[Any, Nothing] { self =>
    def apply(builder: JWebSocketServerProtocolConfig.Builder): JWebSocketServerProtocolConfig.Builder =
      ProtocolConfig.builder(self, builder)
  }
  object ProtocolConfig {
    case class SubProtocol(name: String)                   extends ProtocolConfig
    case object CheckStartsWith                            extends ProtocolConfig
    case class HandshakeTimeoutMillis(duration: Duration)  extends ProtocolConfig
    case class ForceCloseTimeoutMillis(duration: Duration) extends ProtocolConfig
    case object HandleCloseFrames                          extends ProtocolConfig
    case class SendCloseFrame(status: CloseStatus)         extends ProtocolConfig
    case object DropPongFrames                             extends ProtocolConfig

    def builder(
      config: ProtocolConfig,
      builder: JWebSocketServerProtocolConfig.Builder,
    ): JWebSocketServerProtocolConfig.Builder = {
      def loop(
        config: ProtocolConfig,
        b: JWebSocketServerProtocolConfig.Builder,
      ): JWebSocketServerProtocolConfig.Builder =
        config match {
          case SubProtocol(name)                 => b.subprotocols(name)
          case CheckStartsWith                   => b.checkStartsWith(true)
          case HandshakeTimeoutMillis(duration)  => b.handshakeTimeoutMillis(duration.toMillis)
          case ForceCloseTimeoutMillis(duration) => b.forceCloseTimeoutMillis(duration.toMillis)
          case HandleCloseFrames                 => b.handleCloseFrames(true)
          case SendCloseFrame(status)            => b.sendCloseFrame(status.asJava)
          case DropPongFrames                    => b.dropPongFrames(true)
        }

      loop(config, builder)
    }
  }

  sealed trait DecoderConfig extends Socket[Any, Nothing] { self =>
    def apply(builder: JWebSocketDecoderConfig.Builder): JWebSocketDecoderConfig.Builder =
      DecoderConfig.builder(self, builder)
  }
  object DecoderConfig {
    case class DecoderMaxFramePayloadLength(length: Int) extends DecoderConfig
    case object ExpectMaskedFrames                       extends DecoderConfig
    case object AllowMaskMismatch                        extends DecoderConfig
    case object AllowExtensions                          extends DecoderConfig
    case object CloseOnProtocolViolation                 extends DecoderConfig
    case object WithUTF8Validator                        extends DecoderConfig

    def builder(config: DecoderConfig, builder: JWebSocketDecoderConfig.Builder): JWebSocketDecoderConfig.Builder = {
      def loop(config: DecoderConfig, b: JWebSocketDecoderConfig.Builder): JWebSocketDecoderConfig.Builder =
        config match {
          case DecoderMaxFramePayloadLength(length) => b.maxFramePayloadLength(length)
          case ExpectMaskedFrames                   => b.expectMaskedFrames(true)
          case AllowMaskMismatch                    => b.allowMaskMismatch(true)
          case AllowExtensions                      => b.allowExtensions(true)
          case CloseOnProtocolViolation             => b.closeOnProtocolViolation(true)
          case WithUTF8Validator                    => b.withUTF8Validator(true)
        }

      loop(config, builder)
    }
  }

  /**
   * Used to specify the websocket sub-protocol
   */
  def subProtocol(name: String): Socket[Any, Nothing] = ProtocolConfig.SubProtocol(name)

  /**
   * Called when the connection is successfully upgrade to a websocket one. In case of a failure on the returned stream,
   * the socket is forcefully closed.
   */
  def open[R, E](onOpen: Connection => ZStream[R, E, WebSocketFrame]): Socket[R, E] = OnOpen(onOpen)

  /**
   * Called on every incoming WebSocketFrame. In case of a failure on the returned stream, the socket is forcefully
   * closed.
   */
  def message[R, E](onMessage: WebSocketFrame => ZStream[R, E, WebSocketFrame]): Socket[R, E] =
    OnMessage(onMessage)

  /**
   * Collects the incoming messages using a partial function. In case of a failure on the returned stream, the socket is
   * forcefully closed.
   */
  def collect[R, E](onMessage: PartialFunction[WebSocketFrame, ZStream[R, E, WebSocketFrame]]): Socket[R, E] =
    message(ws => if (onMessage.isDefinedAt(ws)) onMessage(ws) else ZStream.empty)

  /**
   * Called whenever there is an error on the channel after a successful upgrade to websocket.
   */
  def error[R](onError: Throwable => ZIO[R, Nothing, Unit]): Socket[R, Nothing] = OnError(onError)

  /**
   * Called when the websocket connection is closed successfully.
   */
  def close[R](onClose: (Connection) => ZIO[R, Nothing, Unit]): Socket[R, Nothing] = OnClose(onClose)

  def settings[R, E](ss: Socket[R, E]): Settings[R, E] = {
    val protocolBuilder: JWebSocketServerProtocolConfig.Builder = JWebSocketServerProtocolConfig
      .newBuilder()
      .handleCloseFrames(false)
      .dropPongFrames(false)

    val decoderBuilder = JWebSocketDecoderConfig
      .newBuilder()
      .expectMaskedFrames(false)
      .closeOnProtocolViolation(false)
      .withUTF8Validator(false)

    def loop(ss: Socket[R, E], s: Settings[R, E]): Settings[R, E] = ss match {
      case OnOpen(onOpen)       => s.copy(onOpen = onOpen)
      case OnMessage(onMessage) => s.copy(onMessage = ws => s.onMessage(ws).merge(onMessage(ws)))
      case OnError(onError)     => s.copy(onError = onError)
      case OnClose(onClose)     => s.copy(onClose = onClose)
      case Concat(a, b)         => loop(b, loop(a, s))
      case c: ProtocolConfig    =>
        c(protocolBuilder)
        s
      case c: DecoderConfig     =>
        c(decoderBuilder)
        s

    }

    loop(ss, Settings(config = protocolBuilder.decoderConfig(decoderBuilder.build())))
  }
}
