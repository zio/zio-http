package zhttp.socket

import zio._
import zio.stream.ZStream

import java.net.{SocketAddress => JSocketAddress}

sealed trait Socket[-R, +E] { self =>
  def ++[R1 <: R, E1 >: E](other: Socket[R1, E1]): Socket[R1, E1] = Socket.Concat(self, other)
  lazy val settings: SocketConfig[R, E]                           = SocketConfig.fromSocket(self)
}

object Socket {
  type Connection = JSocketAddress
  type Cause      = Option[Throwable]

  case class Concat[R, E](a: Socket[R, E], b: Socket[R, E])                              extends Socket[R, E]
  case class OnOpen[R, E](onOpen: Connection => ZStream[R, E, WebSocketFrame])           extends Socket[R, E]
  case class OnMessage[R, E](onMessage: WebSocketFrame => ZStream[R, E, WebSocketFrame]) extends Socket[R, E]
  case class OnError[R](onError: Throwable => ZIO[R, Nothing, Unit])                     extends Socket[R, Nothing]
  case class OnClose[R](onClose: Connection => ZIO[R, Nothing, Unit])                    extends Socket[R, Nothing]
  case class OnTimeout[R](onTimeout: ZIO[R, Nothing, Unit])                              extends Socket[R, Nothing]
  case class Protocol(config: ProtocolConfig)                                            extends Socket[Any, Nothing]
  case class Decoder(config: DecoderConfig)                                              extends Socket[Any, Nothing]

  def protocol(config: ProtocolConfig): Socket[Any, Nothing] = Protocol(config)
  def decoder(config: DecoderConfig): Socket[Any, Nothing]   = Decoder(config)

  /**
   * Called when the connection is successfully upgrade to a websocket one. In case of a failure on the returned stream,
   * the socket is forcefully closed.
   */
  def open[R, E](onOpen: Connection => ZStream[R, E, WebSocketFrame]): Socket[R, E] = OnOpen(onOpen)

  /**
   * Called when the handshake gets timeout.
   */
  def timeout[R](onTimeout: ZIO[R, Nothing, Unit]): Socket[R, Nothing] = OnTimeout(onTimeout)

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
  def collect[A] = new MkCollectSome[A](())

  /**
   * Called whenever there is an error on the channel after a successful upgrade to websocket.
   */
  def error[R](onError: Throwable => ZIO[R, Nothing, Unit]): Socket[R, Nothing] = OnError(onError)

  /**
   * Called when the websocket connection is closed successfully.
   */
  def close[R](onClose: (Connection) => ZIO[R, Nothing, Unit]): Socket[R, Nothing] = OnClose(onClose)

  final class MkCollectSome[A](val unit: Unit) extends AnyVal {
    def apply[R, E, E1, B](
      pf: PartialFunction[A, ZStream[R, E, B]],
    )(implicit e: SocketEncoder[B], d: SocketDecoder[E1, A], ev: E1 <:< E): Socket[R, E] =
      message { ws =>
        d.decode(ws) match {
          case Left(cause) => ZStream.fail(cause)
          case Right(a)    => if (pf.isDefinedAt(a)) pf(a).map(e.encode) else ZStream.empty
        }
      }
  }
}
