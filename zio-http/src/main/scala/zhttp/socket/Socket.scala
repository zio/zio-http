package zhttp.socket

import zio._
import zio.duration.Duration
import zio.stream.ZStream

import java.net.{SocketAddress => JSocketAddress}

sealed trait Socket[-R, +E] { self =>
  def ++[R1 <: R, E1 >: E](other: Socket[R1, E1]): Socket[R1, E1] = Socket.Concat(self, other)
  lazy val settings: SocketConfig[R, E]                           = SocketConfig.fromSocket(self)
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
    case class OnTimeout[R](onTimeout: ZIO[R, Nothing, Unit])                              extends HandlerConfig[R, Nothing]
  }

  sealed trait ProtocolConfig extends Socket[Any, Nothing]
  object ProtocolConfig {
    case class SubProtocol(name: String)                     extends ProtocolConfig
    case class HandshakeTimeoutMillis(duration: Duration)    extends ProtocolConfig
    case class ForceCloseTimeoutMillis(duration: Duration)   extends ProtocolConfig
    case object ForwardCloseFrames                           extends ProtocolConfig
    case class SendCloseFrame(status: CloseStatus)           extends ProtocolConfig
    case class SendCloseFrameCode(code: Int, reason: String) extends ProtocolConfig
    case object ForwardPongFrames                            extends ProtocolConfig

  }

  sealed trait DecoderConfig extends Socket[Any, Nothing]
  object DecoderConfig {
    case class MaxFramePayloadLength(length: Int) extends DecoderConfig
    case object RejectMaskedFrames                extends DecoderConfig
    case object AllowMaskMismatch                 extends DecoderConfig
    case object AllowExtensions                   extends DecoderConfig
    case object AllowProtocolViolation            extends DecoderConfig
    case object SkipUTF8Validation                extends DecoderConfig
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
   * Called when the handshake gets timeout.
   */
  def timeout[R](onTimeout: ZIO[R, Nothing, Unit]): Socket[R, Nothing] = HandlerConfig.OnTimeout(onTimeout)

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
  def handshakeTimeout(duration: Duration): Socket[Any, Nothing] = ProtocolConfig.HandshakeTimeoutMillis(duration)

  /**
   * Close the connection if it was not closed by the client after timeout specified
   */
  def forceCloseTimeout(duration: Duration): Socket[Any, Nothing] =
    ProtocolConfig.ForceCloseTimeoutMillis(duration)

  /**
   * Close frames should be forwarded
   */
  def forwardCloseFrames: Socket[Any, Nothing] = ProtocolConfig.ForwardCloseFrames

  /**
   * Close frame to send, when close frame was not send manually.
   */
  def closeFrame(status: CloseStatus): Socket[Any, Nothing] = ProtocolConfig.SendCloseFrame(status)

  /**
   * Close frame to send, when close frame was not send manually.
   */
  def closeFrame(code: Int, reason: String): Socket[Any, Nothing] = ProtocolConfig.SendCloseFrameCode(code, reason)

  /**
   * If pong frames should be forwarded
   */
  def forwardPongFrames: Socket[Any, Nothing] = ProtocolConfig.ForwardPongFrames

  /**
   * Sets Maximum length of a frame's payload. Setting this to an appropriate value for you application helps check for
   * denial of services attacks.
   */
  def maxFramePayloadLength(length: Int): Socket[Any, Nothing] =
    DecoderConfig.MaxFramePayloadLength(length)

  /**
   * Web socket servers must set this to true to reject incoming masked payload.
   */
  def rejectMaskedFrames: Socket[Any, Nothing] = DecoderConfig.RejectMaskedFrames

  /**
   * When set to true, frames which are not masked properly according to the standard will still be accepted.
   */
  def allowMaskMismatch: Socket[Any, Nothing] = DecoderConfig.AllowMaskMismatch

  /**
   * Allow extensions to be used in the reserved bits of the web socket frame
   */
  def allowExtensions: Socket[Any, Nothing] = DecoderConfig.AllowExtensions

  /**
   * Flag to not send close frame immediately on any protocol violation.ion.
   */
  def allowProtocolViolation: Socket[Any, Nothing] = DecoderConfig.AllowProtocolViolation

  /**
   * Allows you to avoid adding of Utf8FrameValidator to the pipeline on the WebSocketServerProtocolHandler creation.
   * This is useful (less overhead) when you use only BinaryWebSocketFrame within your web socket connection.
   */
  def skipUTF8Validation: Socket[Any, Nothing] = DecoderConfig.SkipUTF8Validation
}
