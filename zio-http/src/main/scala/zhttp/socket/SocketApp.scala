package zhttp.socket

import zhttp.http.Response
import zhttp.service.{ChannelFactory, Client, EventLoopGroup}
import zhttp.socket.SocketApp.Handle.{WithEffect, WithSocket}
import zhttp.socket.SocketApp.{Connection, Handle}
import zio.stream.ZStream
import zio.{NeedsEnv, ZIO, ZManaged}

import java.net.SocketAddress

final case class SocketApp[-R](
  timeout: Option[ZIO[R, Nothing, Any]] = None,
  open: Option[Handle[R]] = None,
  message: Option[Socket[R, Throwable, WebSocketFrame, WebSocketFrame]] = None,
  error: Option[Throwable => ZIO[R, Nothing, Any]] = None,
  close: Option[Connection => ZIO[R, Nothing, Any]] = None,
  decoder: SocketDecoder = SocketDecoder.default,
  protocol: SocketProtocol = SocketProtocol.default,
) { self =>

  /**
   * Creates a socket connection on the provided URL. Typically used to connect
   * as a client.
   */
  def connect(url: String): ZManaged[R with EventLoopGroup with ChannelFactory, Throwable, Response] =
    Client.socket(url, self)

  /**
   * Called when the websocket connection is closed successfully.
   */
  def onClose[R1 <: R](close: Connection => ZIO[R1, Nothing, Any]): SocketApp[R1] =
    copy(close = self.close match {
      case Some(value) => Some((connection: Connection) => value(connection) *> close(connection))
      case None        => Some(close)
    })

  /**
   * Called whenever there is an error on the channel after a successful upgrade
   * to websocket.
   */
  def onError[R1 <: R](error: Throwable => ZIO[R1, Nothing, Any]): SocketApp[R1] =
    copy(error = self.error match {
      case Some(value) => Some((cause: Throwable) => value(cause) *> error(cause))
      case None        => Some(error)
    })

  /**
   * Called on every incoming WebSocketFrame. In case of a failure on the
   * returned stream, the socket is forcefully closed.
   */
  def onMessage[R1 <: R](message: Socket[R1, Throwable, WebSocketFrame, WebSocketFrame]): SocketApp[R1] =
    copy(message = self.message match {
      case Some(other) => Some(other merge message)
      case None        => Some(message)
    })

  /**
   * Called when the connection is successfully upgraded to a websocket one. In
   * case of a failure, the socket is forcefully closed.
   */
  def onOpen[R1 <: R](open: Socket[R1, Throwable, Connection, WebSocketFrame]): SocketApp[R1] =
    onOpen(Handle(open))

  /**
   * Called when the connection is successfully upgraded to a websocket one. In
   * case of a failure, the socket is forcefully closed.
   */
  def onOpen[R1 <: R](open: Connection => ZIO[R1, Throwable, Any]): SocketApp[R1] =
    onOpen(Handle(open))

  /**
   * Called when the websocket handshake gets timeout.
   */
  def onTimeout[R1 <: R](timeout: ZIO[R1, Nothing, Any]): SocketApp[R1] =
    copy(timeout = self.timeout match {
      case Some(other) => Some(timeout *> other)
      case None        => Some(timeout)
    })

  /**
   * Provides the socket app with its required environment, which eliminates its
   * dependency on `R`.
   */
  def provideEnvironment(env: R)(implicit ev: NeedsEnv[R]): SocketApp[Any] =
    self.copy(
      timeout = self.timeout.map(_.provide(env)),
      open = self.open.map(_.provideEnvironment(env)),
      message = self.message.map(_.provideEnvironment(env)),
      error = self.error.map(f => (t: Throwable) => f(t).provide(env)),
      close = self.close.map(f => (c: Connection) => f(c).provide(env)),
    )

  /**
   * Creates a new response from the socket app.
   */
  def toResponse: ZIO[R, Nothing, Response] =
    ZIO.environment[R].flatMap { env =>
      Response.fromSocketApp(self.provideEnvironment(env))
    }

  /**
   * Frame decoder configuration
   */
  def withDecoder(decoder: SocketDecoder): SocketApp[R] =
    copy(decoder = self.decoder ++ decoder)

  /**
   * Server side websocket configuration
   */
  def withProtocol(protocol: SocketProtocol): SocketApp[R] =
    copy(protocol = self.protocol ++ protocol)

  /**
   * Called when the connection is successfully upgraded to a websocket one. In
   * case of a failure, the socket is forcefully closed.
   */
  private def onOpen[R1 <: R](open: Handle[R1]): SocketApp[R1] =
    copy(open = self.open.fold(Some(open))(other => Some(other merge open)))
}

object SocketApp {
  type Connection = SocketAddress

  def apply[R, E, A, B](socket: Socket[R, E, A, B])(implicit ev: IsWebSocket[R, E, A, B]): SocketApp[R] =
    SocketApp(message = Some(ev(socket)))

  private[zhttp] sealed trait Handle[-R] { self =>
    def merge[R1 <: R](other: Handle[R1]): Handle[R1] = {
      (self, other) match {
        case (WithSocket(s0), WithSocket(s1)) => WithSocket(s0 merge s1)
        case (WithEffect(f0), WithEffect(f1)) => WithEffect(c => f0(c) <&> f1(c))
        case (a, b)                           => a.sock merge b.sock
      }
    }

    def provideEnvironment(r: R)(implicit ev: NeedsEnv[R]): Handle[Any] =
      self match {
        case WithEffect(f) => WithEffect(c => f(c).provide(r))
        case WithSocket(s) => WithSocket(s.provideEnvironment(r))
      }

    private def sock: Handle[R] = self match {
      case WithEffect(f)     => WithSocket(Socket.fromFunction(c => ZStream.fromEffect(f(c)) *> ZStream.empty))
      case s @ WithSocket(_) => s
    }
  }

  private[zhttp] object Handle {
    def apply[R](onOpen: Socket[R, Throwable, Connection, WebSocketFrame]): Handle[R] = WithSocket(onOpen)

    def apply[R](onOpen: Connection => ZIO[R, Throwable, Any]): Handle[R] = WithEffect(onOpen)

    final case class WithEffect[R](f: Connection => ZIO[R, Throwable, Any]) extends Handle[R]

    final case class WithSocket[R](s: Socket[R, Throwable, Connection, WebSocketFrame]) extends Handle[R]
  }
}
