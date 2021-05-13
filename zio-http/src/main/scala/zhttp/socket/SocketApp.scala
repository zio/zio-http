package zhttp.socket

import zhttp.http.Response
import zio._

import java.net.{SocketAddress => JSocketAddress}

sealed trait SocketApp[-R, +E] { self =>
  def ++[R1 <: R, E1 >: E](other: SocketApp[R1, E1]): SocketApp[R1, E1] = SocketApp.Concat(self, other)
  def config: SocketApp.SocketConfig[R, E]                              = SocketApp.asSocketConfig(self)
  def asResponse: Response[R, E]                                        = Response.SocketResponse(self)
}

object SocketApp {
  type Connection = JSocketAddress
  type Cause      = Option[Throwable]

  private final case class Concat[R, E](a: SocketApp[R, E], b: SocketApp[R, E])           extends SocketApp[R, E]
  private final case class OnOpen[R, E](onOpen: Socket[R, E, Connection, WebSocketFrame]) extends SocketApp[R, E]
  private final case class OnMessage[R, E](onMessage: Socket[R, E, WebSocketFrame, WebSocketFrame])
      extends SocketApp[R, E]
  private final case class OnError[R](onError: Throwable => ZIO[R, Nothing, Unit])        extends SocketApp[R, Nothing]
  private final case class OnClose[R](onClose: Connection => ZIO[R, Nothing, Unit])       extends SocketApp[R, Nothing]
  private final case class OnTimeout[R](onTimeout: ZIO[R, Nothing, Unit])                 extends SocketApp[R, Nothing]
  private final case class Protocol(protocol: SocketProtocol)                             extends SocketApp[Any, Nothing]
  private final case class Decoder(decoder: SocketDecoder)                                extends SocketApp[Any, Nothing]
  private case object Empty                                                               extends SocketApp[Any, Nothing]

  /**
   * Called when the connection is successfully upgrade to a websocket one. In case of a failure on the returned stream,
   * the socket is forcefully closed.
   */
  def open[R, E](onOpen: Socket[R, E, Connection, WebSocketFrame]): SocketApp[R, E] =
    SocketApp.OnOpen(onOpen)

  /**
   * Called when the handshake gets timeout.
   */
  def timeout[R](onTimeout: ZIO[R, Nothing, Unit]): SocketApp[R, Nothing] = SocketApp.OnTimeout(onTimeout)

  /**
   * Called on every incoming WebSocketFrame. In case of a failure on the returned stream, the socket is forcefully
   * closed.
   */
  def message[R, E](onMessage: Socket[R, E, WebSocketFrame, WebSocketFrame]): SocketApp[R, E] =
    SocketApp.OnMessage(onMessage)

  /**
   * Called whenever there is an error on the channel after a successful upgrade to websocket.
   */
  def error[R](onError: Throwable => ZIO[R, Nothing, Unit]): SocketApp[R, Nothing] = SocketApp.OnError(onError)

  /**
   * Called when the websocket connection is closed successfully.
   */
  def close[R](onClose: (Connection) => ZIO[R, Nothing, Unit]): SocketApp[R, Nothing] =
    SocketApp.OnClose(onClose)

  /**
   * Frame decoder configuration
   */
  def decoder(decoder: SocketDecoder): SocketApp[Any, Nothing] = Decoder(decoder)

  /**
   * Server side websocket configuration
   */
  def protocol(protocol: SocketProtocol): SocketApp[Any, Nothing] = Protocol(protocol)

  def protocol(name: String): SocketApp[Any, Nothing] = Protocol(SocketProtocol.subProtocol(name))

  /**
   * Creates a new empty socket handler
   */
  def empty: SocketApp[Any, Nothing] = Empty

  // TODO: rename to HandlerConfig
  final case class SocketConfig[-R, +E](
    onTimeout: Option[ZIO[R, Nothing, Unit]] = None,
    onOpen: Option[Socket[R, E, Connection, WebSocketFrame]] = None,
    onMessage: Option[Socket[R, E, WebSocketFrame, WebSocketFrame]] = None,
    onError: Option[Throwable => ZIO[R, Nothing, Unit]] = None,
    onClose: Option[Connection => ZIO[R, Nothing, Unit]] = None,
    decoder: SocketDecoder = SocketDecoder.default,
    protocol: SocketProtocol = SocketProtocol.default,
  )

  def asSocketConfig[R, E](socket: SocketApp[R, E]): SocketConfig[R, E] = {
    def loop(config: SocketApp[R, E], s: SocketConfig[R, E]): SocketConfig[R, E] =
      config match {
        case Empty => s

        case Decoder(decoder) => s.copy(decoder = s.decoder ++ decoder)

        case Protocol(protocol) => s.copy(protocol = s.protocol ++ protocol)

        case OnTimeout(onTimeout) =>
          s.copy(onTimeout = s.onTimeout.fold(Option(onTimeout))(v => Option(v &> onTimeout)))

        case OnOpen(a) =>
          s.copy(onOpen = s.onOpen match {
            case Some(b) => Option(b merge a)
            case None    => Option(a)
          })

        case OnMessage(a) =>
          s.copy(onMessage = s.onMessage match {
            case Some(b) => Option(b merge a)
            case None    => Option(a)
          })

        case OnError(onError) =>
          s.copy(onError = s.onError.fold(Option(onError))(v => Option(c => v(c) &> onError(c))))

        case OnClose(onClose) =>
          s.copy(onClose = s.onClose.fold(Option(onClose))(v => Option(c => v(c) &> onClose(c))))

        case Concat(a, b) =>
          loop(b, loop(a, s))
      }

    loop(socket, SocketConfig[R, E]())
  }

}
