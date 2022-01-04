package zhttp.socket

import zhttp.http.Response
import zhttp.socket.SocketApp.Handle.{WithEffect, WithSocket}
import zhttp.socket.SocketApp.{Connection, Handle}
import zio.ZIO
import zio.stream.ZStream

import java.net.SocketAddress

final case class SocketApp[-R, +E](
  timeout: Option[ZIO[R, Nothing, Any]] = None,
  open: Option[Handle[R, E]] = None,
  message: Option[Socket[R, E, WebSocketFrame, WebSocketFrame]] = None,
  error: Option[Throwable => ZIO[R, Nothing, Any]] = None,
  close: Option[Connection => ZIO[R, Nothing, Any]] = None,
  decoder: SocketDecoder = SocketDecoder.default,
  protocol: SocketProtocol = SocketProtocol.default,
) { self =>

  /**
   * Creates a new WebSocket Response
   */
  def asResponse: Response[R, E] = Response.socket(self)

  /**
   * Called when the websocket connection is closed successfully.
   */
  def onClose[R1 <: R](close: Connection => ZIO[R1, Nothing, Any]): SocketApp[R1, E] =
    copy(close = self.close match {
      case Some(value) => Some(connection => value(connection) *> close(connection))
      case None        => Some(close)
    })

  /**
   * Called whenever there is an error on the channel after a successful upgrade to websocket.
   */
  def onError[R1 <: R](error: Throwable => ZIO[R1, Nothing, Any]): SocketApp[R1, E] =
    copy(error = self.error match {
      case Some(value) => Some(cause => value(cause) *> error(cause))
      case None        => Some(error)
    })

  /**
   * Called on every incoming WebSocketFrame. In case of a failure on the returned stream, the socket is forcefully
   * closed.
   */
  def onMessage[R1 <: R, E1 >: E](message: Socket[R1, E1, WebSocketFrame, WebSocketFrame]): SocketApp[R1, E1] =
    copy(message = self.message match {
      case Some(other) => Some(other merge message)
      case None        => Some(message)
    })

  /**
   * Called when the connection is successfully upgraded to a websocket one. In case of a failure, the socket is
   * forcefully closed.
   */
  def onOpen[R1 <: R, E1 >: E](open: Socket[R1, E1, Connection, WebSocketFrame]): SocketApp[R1, E1] =
    onOpen(Handle(open))

  /**
   * Called when the connection is successfully upgraded to a websocket one. In case of a failure, the socket is
   * forcefully closed.
   */
  def onOpen[R1 <: R, E1 >: E](open: Connection => ZIO[R1, E1, Any]): SocketApp[R1, E1] =
    onOpen(Handle(open))

  /**
   * Called when the websocket handshake gets timeout.
   */
  def onTimeout[R1 <: R](timeout: ZIO[R1, Nothing, Any]): SocketApp[R1, E] =
    copy(timeout = self.timeout match {
      case Some(other) => Some(timeout *> other)
      case None        => Some(timeout)
    })

  /**
   * Frame decoder configuration
   */
  def withDecoder(decoder: SocketDecoder): SocketApp[R, E] =
    copy(decoder = self.decoder ++ decoder)

  /**
   * Server side websocket configuration
   */
  def withProtocol(protocol: SocketProtocol): SocketApp[R, E] =
    copy(protocol = self.protocol ++ protocol)

  /**
   * Called when the connection is successfully upgraded to a websocket one. In case of a failure, the socket is
   * forcefully closed.
   */
  private def onOpen[R1 <: R, E1 >: E](open: Handle[R1, E1]): SocketApp[R1, E1] =
    copy(open = self.open.fold(Some(open))(other => Some(other merge open)))
}

object SocketApp {
  type Connection = SocketAddress

  def apply[R, E](socket: Socket[R, E, WebSocketFrame, WebSocketFrame]): SocketApp[R, E] =
    SocketApp(message = Some(socket))

  private[zhttp] sealed trait Handle[-R, +E] { self =>
    def merge[R1 <: R, E1 >: E](other: Handle[R1, E1]): Handle[R1, E1] = {
      (self, other) match {
        case (WithSocket(s0), WithSocket(s1)) => WithSocket(s0 merge s1)
        case (WithEffect(f0), WithEffect(f1)) => WithEffect(c => f0(c) <&> f1(c))
        case (a, b)                           => a.sock merge b.sock
      }
    }

    private def sock: Handle[R, E] = self match {
      case WithEffect(f)     => WithSocket(Socket.fromFunction(c => ZStream.fromEffect(f(c)) *> ZStream.empty))
      case s @ WithSocket(_) => s
    }
  }

  private[zhttp] object Handle {
    def apply[R, E](onOpen: Socket[R, E, Connection, WebSocketFrame]): Handle[R, E] = WithSocket(onOpen)

    def apply[R, E](onOpen: Connection => ZIO[R, E, Any]): Handle[R, E] = WithEffect(onOpen)

    final case class WithEffect[R, E](f: Connection => ZIO[R, E, Any]) extends Handle[R, E]

    final case class WithSocket[R, E](s: Socket[R, E, Connection, WebSocketFrame]) extends Handle[R, E]
  }
}
