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
   * {@code true} to handle all requests, where URI path component starts from {@link WebSocketServerProtocolConfig#
   * websocketPath ( )}, {@code false} for exact match (default).
   */
  def checkStartsWith: Socket[Any, Nothing] = ProtocolConfig.CheckStartsWith

  /**
   * Handshake timeout in mills, when handshake timeout, will trigger user event {@link ClientHandshakeStateEvent#
   * HANDSHAKE_TIMEOUT}
   */
  def handshakeTimeoutMillis(duration: Duration): Socket[Any, Nothing] = ProtocolConfig.HandshakeTimeoutMillis(duration)

  /**
   * Close the connection if it was not closed by the client after timeout specified
   */
  def forceCloseTimeoutMillis(duration: Duration): Socket[Any, Nothing] =
    ProtocolConfig.ForceCloseTimeoutMillis(duration)

  /**
   * {@code true} if close frames should not be forwarded and just close the channel
   */
  def handleCloseFrames: Socket[Any, Nothing] = ProtocolConfig.HandleCloseFrames

  /**
   * Close frame to send, when close frame was not send manually. Or {@code null} to disable proper close.
   */
  def sendCloseFrame(status: CloseStatus): Socket[Any, Nothing] = ProtocolConfig.SendCloseFrame(status)

  /**
   * {@code true} if pong frames should not be forwarded
   */
  def dropPongFrames: Socket[Any, Nothing] = ProtocolConfig.DropPongFrames

  /**
   * Sets Maximum length of a frame's payload. Setting this to an appropriate value for you application helps check for
   * denial of services attacks.
   */
  def decoderMaxFramePayloadLength(length: Int): Socket[Any, Nothing] =
    DecoderConfig.DecoderMaxFramePayloadLength(length)

  /**
   * Web socket servers must set this to true processed incoming masked payload. Client implementations must set this to
   * false.
   */
  def expectMaskedFrames: Socket[Any, Nothing] = DecoderConfig.ExpectMaskedFrames

  /**
   * When set to true, frames which are not masked properly according to the standard will still be accepted.
   */
  def allowMaskMismatch: Socket[Any, Nothing] = DecoderConfig.AllowMaskMismatch

  /**
   * Allow extensions to be used in the reserved bits of the web socket frame
   */
  def allowExtensions: Socket[Any, Nothing] = DecoderConfig.AllowExtensions

  /**
   * Flag to send close frame immediately on any protocol violation.ion.
   */
  def closeOnProtocolViolation: Socket[Any, Nothing] = DecoderConfig.CloseOnProtocolViolation

  /**
   * Allows you to avoid adding of Utf8FrameValidator to the pipeline on the WebSocketServerProtocolHandler creation.
   * This is useful (less overhead) when you use only BinaryWebSocketFrame within your web socket connection.
   */
  def withUTF8Validator: Socket[Any, Nothing] = DecoderConfig.WithUTF8Validator

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
