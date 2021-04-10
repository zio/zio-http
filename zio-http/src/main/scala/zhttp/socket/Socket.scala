package zhttp.socket

import io.netty.handler.codec.http.websocketx.{WebSocketServerProtocolConfig => JWebSocketServerProtocolConfig}
import zio._
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
    config: JWebSocketServerProtocolConfig.Builder = JWebSocketServerProtocolConfig.newBuilder(),
  )

  private case class SubProtocol(name: String)                                                   extends Socket[Any, Nothing]
  private case class OnOpen[R, E](onOpen: Connection => ZStream[R, E, WebSocketFrame])           extends Socket[R, E]
  private case class OnMessage[R, E](onMessage: WebSocketFrame => ZStream[R, E, WebSocketFrame]) extends Socket[R, E]
  private case class OnError[R](onError: Throwable => ZIO[R, Nothing, Unit])                     extends Socket[R, Nothing]
  private case class OnClose[R](onClose: Connection => ZIO[R, Nothing, Unit])                    extends Socket[R, Nothing]
  private case class Concat[R, E](a: Socket[R, E], b: Socket[R, E])                              extends Socket[R, E]

  /**
   * Used to specify the websocket sub-protocol
   */
  def subProtocol(name: String): Socket[Any, Nothing] = SubProtocol(name)

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
    def loop(ss: Socket[R, E], s: Settings[R, E]): Settings[R, E] = ss match {
      case SubProtocol(name)    => s.copy(config = s.config.subprotocols(name))
      case OnOpen(onOpen)       => s.copy(onOpen = onOpen)
      case OnMessage(onMessage) => s.copy(onMessage = ws => s.onMessage(ws).merge(onMessage(ws)))
      case OnError(onError)     => s.copy(onError = onError)
      case OnClose(onClose)     => s.copy(onClose = onClose)
      case Concat(a, b)         => loop(b, loop(a, s))
    }

    loop(ss, Settings())
  }
}
