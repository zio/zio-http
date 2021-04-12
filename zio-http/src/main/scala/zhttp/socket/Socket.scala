package zhttp.socket

import zio._
import zio.duration.Duration
import zio.stream.ZStream

import java.net.{SocketAddress => JSocketAddress}

sealed trait Socket[-R, +E] { self =>
  def <+>[R1 <: R, E1 >: E](other: Socket[R1, E1]): Socket[R1, E1] = Socket.Concat(self, other)
  lazy val settings: SocketConfig[R, E]                            = SocketConfig.fromSocket(self)
}

object Socket {
  type Connection = JSocketAddress
  type Cause      = Option[Throwable]

  case class Concat[R, E](a: Socket[R, E], b: Socket[R, E]) extends Socket[R, E]

  sealed trait HandlerConfig[-R, +E] extends Socket[R, E]
  object HandlerConfig {
    case class OnOpen[R, E](onOpen: Connection => ZStream[R, E, WebSocketFrame])           extends HandlerConfig[R, E]
    case class OnMessage[R, E](onMessage: WebSocketFrame => ZStream[R, E, WebSocketFrame]) extends HandlerConfig[R, E]
    case class OnError[R](onError: Throwable => ZIO[R, Nothing, Unit])                     extends HandlerConfig[R, Nothing]
    case class OnClose[R](onClose: Connection => ZIO[R, Nothing, Unit])                    extends HandlerConfig[R, Nothing]
  }

  sealed trait ProtocolConfig extends Socket[Any, Nothing]
  object ProtocolConfig {
    case class SubProtocol(name: String)                   extends ProtocolConfig
    case class HandshakeTimeoutMillis(duration: Duration)  extends ProtocolConfig
    case class ForceCloseTimeoutMillis(duration: Duration) extends ProtocolConfig
    case object HandleCloseFrames                          extends ProtocolConfig
    case class SendCloseFrame(status: CloseStatus)         extends ProtocolConfig
    case object DropPongFrames                             extends ProtocolConfig

  }

  sealed trait DecoderConfig extends Socket[Any, Nothing]
  object DecoderConfig {
    case class DecoderMaxFramePayloadLength(length: Int) extends DecoderConfig
    case class ExpectMaskedFrames(flag: Boolean)         extends DecoderConfig
    case class AllowMaskMismatch(flag: Boolean)          extends DecoderConfig
    case object AllowExtensions                          extends DecoderConfig
    case object CloseOnProtocolViolation                 extends DecoderConfig
    case object WithUTF8Validator                        extends DecoderConfig
  }

  /**
   * Used to specify the websocket sub-protocol
   */
  def subProtocol(name: String): Socket[Any, Nothing] = ProtocolConfig.SubProtocol(name)

  /**
   * Called when the connection is successfully upgrade to a websocket one. In case of a failure on the returned stream,
   * the socket is forcefully closed.
   */
  def open[R, E](onOpen: Connection => ZStream[R, E, WebSocketFrame]): Socket[R, E] = HandlerConfig.OnOpen(onOpen)

  /**
   * Called on every incoming WebSocketFrame. In case of a failure on the returned stream, the socket is forcefully
   * closed.
   */
  def message[R, E](onMessage: WebSocketFrame => ZStream[R, E, WebSocketFrame]): Socket[R, E] =
    HandlerConfig.OnMessage(onMessage)

  /**
   * Collects the incoming messages using a partial function. In case of a failure on the returned stream, the socket is
   * forcefully closed.
   */
  def collect[R, E](onMessage: PartialFunction[WebSocketFrame, ZStream[R, E, WebSocketFrame]]): Socket[R, E] =
    message(ws => if (onMessage.isDefinedAt(ws)) onMessage(ws) else ZStream.empty)

  /**
   * Called whenever there is an error on the channel after a successful upgrade to websocket.
   */
  def error[R](onError: Throwable => ZIO[R, Nothing, Unit]): Socket[R, Nothing] = HandlerConfig.OnError(onError)

  /**
   * Called when the websocket connection is closed successfully.
   */
  def close[R](onClose: (Connection) => ZIO[R, Nothing, Unit]): Socket[R, Nothing] = HandlerConfig.OnClose(onClose)

  /**
   * Handshake timeout in mills
   */
  def handshakeTimeoutMillis(duration: Duration): Socket[Any, Nothing] = ProtocolConfig.HandshakeTimeoutMillis(duration)

  /**
   * Close the connection if it was not closed by the client after timeout specified
   */
  def forceCloseTimeoutMillis(duration: Duration): Socket[Any, Nothing] =
    ProtocolConfig.ForceCloseTimeoutMillis(duration)

  /**
   * Close frames should not be forwarded and just close the channel
   */
  def handleCloseFrames: Socket[Any, Nothing] = ProtocolConfig.HandleCloseFrames

  /**
   * Close frame to send, when close frame was not send manually.
   */
  def sendCloseFrame(status: CloseStatus): Socket[Any, Nothing] = ProtocolConfig.SendCloseFrame(status)

  /**
   * If pong frames should not be forwarded
   */
  def dropPongFrames: Socket[Any, Nothing] = ProtocolConfig.DropPongFrames

  /**
   * Sets Maximum length of a frame's payload. Setting this to an appropriate value for you application helps check for
   * denial of services attacks.
   */
  def decoderMaxFramePayloadLength(length: Int): Socket[Any, Nothing] =
    DecoderConfig.DecoderMaxFramePayloadLength(length)

  /**
   * Web socket servers must set this to true to process incoming masked payload.
   */
  def expectMaskedFrames(flag: Boolean): Socket[Any, Nothing] = DecoderConfig.ExpectMaskedFrames(flag)

  /**
   * When set to true, frames which are not masked properly according to the standard will still be accepted.
   */
  def allowMaskMismatch(flag: Boolean): Socket[Any, Nothing] = DecoderConfig.AllowMaskMismatch(flag)

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
}
