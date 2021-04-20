package zhttp.socket

import zio._
import zio.stream.ZStream

import java.net.{SocketAddress => JSocketAddress}

sealed trait SocketChannel[-R, +E] { self =>
  def ++[R1 <: R, E1 >: E](other: SocketChannel[R1, E1]): SocketChannel[R1, E1] = SocketChannel.Concat(self, other)
  def config: SocketChannel.SocketConfig[R, E]                                  = SocketChannel.SocketConfig(self)
  def asSocket: SocketB[R, E]                                                   = SocketB.config(self)
}

object SocketChannel {
  type Connection = JSocketAddress
  type Cause      = Option[Throwable]

  private case class Concat[R, E](a: SocketChannel[R, E], b: SocketChannel[R, E])      extends SocketChannel[R, E]
  private case class OnOpen[R, E](onOpen: Connection => ZStream[R, E, WebSocketFrame]) extends SocketChannel[R, E]
  private case class OnMessage[R, E](onMessage: WebSocketFrame => ZStream[R, E, WebSocketFrame])
      extends SocketChannel[R, E]
  private case class OnError[R](onError: Throwable => ZIO[R, Nothing, Unit])           extends SocketChannel[R, Nothing]
  private case class OnClose[R](onClose: Connection => ZIO[R, Nothing, Unit])          extends SocketChannel[R, Nothing]
  private case class OnTimeout[R](onTimeout: ZIO[R, Nothing, Unit])                    extends SocketChannel[R, Nothing]
  private case object Empty                                                            extends SocketChannel[Any, Nothing]

  /**
   * Called when the connection is successfully upgrade to a websocket one. In case of a failure on the returned stream,
   * the socket is forcefully closed.
   */
  def open[R, E](onOpen: Connection => ZStream[R, E, WebSocketFrame]): SocketChannel[R, E] =
    SocketChannel.OnOpen(onOpen)

  /**
   * Called when the handshake gets timeout.
   */
  def timeout[R](onTimeout: ZIO[R, Nothing, Unit]): SocketChannel[R, Nothing] = SocketChannel.OnTimeout(onTimeout)

  /**
   * Called on every incoming WebSocketFrame. In case of a failure on the returned stream, the socket is forcefully
   * closed.
   */
  def message[R, E](onMessage: WebSocketFrame => ZStream[R, E, WebSocketFrame]): SocketChannel[R, E] =
    SocketChannel.OnMessage(onMessage)

  /**
   * Collects the incoming messages using a partial function. In case of a failure on the returned stream, the socket is
   * forcefully closed.
   */
  def collect[R, E](onMessage: PartialFunction[WebSocketFrame, ZStream[R, E, WebSocketFrame]]): SocketChannel[R, E] =
    message(ws => if (onMessage.isDefinedAt(ws)) onMessage(ws) else ZStream.empty)

  /**
   * Called whenever there is an error on the channel after a successful upgrade to websocket.
   */
  def error[R](onError: Throwable => ZIO[R, Nothing, Unit]): SocketChannel[R, Nothing] = SocketChannel.OnError(onError)

  /**
   * Called when the websocket connection is closed successfully.
   */
  def close[R](onClose: (Connection) => ZIO[R, Nothing, Unit]): SocketChannel[R, Nothing] =
    SocketChannel.OnClose(onClose)

  /**
   * Creates a new empty socket handler
   */
  def empty: SocketChannel[Any, Nothing] = Empty

  // TODO: rename to HandlerConfig
  case class SocketConfig[-R, +E](
    onTimeout: Option[ZIO[R, Nothing, Unit]] = None,
    onOpen: Option[Connection => ZStream[R, E, WebSocketFrame]] = None,
    onMessage: Option[WebSocketFrame => ZStream[R, E, WebSocketFrame]] = None,
    onError: Option[Throwable => ZIO[R, Nothing, Unit]] = None,
    onClose: Option[Connection => ZIO[R, Nothing, Unit]] = None,
  )

  object SocketConfig {
    def apply[R, E](socket: SocketChannel[R, E]): SocketConfig[R, E] = {

      def loop(config: SocketChannel[R, E], s: SocketConfig[R, E]): SocketConfig[R, E] =
        config match {
          case Empty => s

          case OnTimeout(onTimeout) =>
            s.copy(onTimeout = s.onTimeout.fold(Option(onTimeout))(v => Option(v &> onTimeout)))

          case OnOpen(onOpen) =>
            s.copy(onOpen = s.onOpen.fold(Option(onOpen))(v => Option((c: Connection) => v(c).merge(onOpen(c)))))

          case OnMessage(onMessage) =>
            s.copy(onMessage = s.onMessage.fold(Option(onMessage))(v => Option(ws => v(ws).merge(onMessage(ws)))))

          case OnError(onError) =>
            s.copy(onError = s.onError.fold(Option(onError))(v => Option(c => v(c) &> onError(c))))

          case OnClose(onClose) =>
            s.copy(onClose = s.onClose.fold(Option(onClose))(v => Option(c => v(c) &> onClose(c))))

          case Concat(a, b) =>
            loop(b, loop(a, s))
        }

      loop(socket, SocketConfig())
    }
  }
}
