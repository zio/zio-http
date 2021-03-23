package zhttp.socket

import zhttp.http.{Response, UResponse}
import zio.ZIO
import zio.stream.ZStream

/**
 * Helps create websocket applications
 */
final case class Socket[-R, +E, -A, +B](asStream: A => ZStream[R, E, B]) { self =>
  def ++[R1 <: R, E1 >: E, A1 <: A, B1 >: B](other: Socket[R1, E1, A1, B1]): Socket[R1, E1, A1, B1] =
    Socket(f => self(f) ++ other(f))

  def <>[R1 <: R, E1, A1 <: A, B1 >: B](other: Socket[R1, E1, A1, B1]): Socket[R1, E1, A1, B1] =
    self.orElse(other)

  def orElse[R1 <: R, E1, A1 <: A, B1 >: B](other: Socket[R1, E1, A1, B1]): Socket[R1, E1, A1, B1] =
    Socket(ws => self(ws) <> other(ws))

  def encode[B1](f: B => B1): Socket[R, E, A, B1] =
    Socket(ws => self(ws).map(f))

  def encodeM[R1 <: R, E1 >: E, B1](f: B => ZIO[R1, E1, B1]): Socket[R1, E1, A, B1] =
    Socket(ws => self(ws).mapM(f))

  def decode[A0](f: A0 => A): Socket[R, E, A0, B] =
    Socket(ws => self(f(ws)))

  def decodeM[A0]: Socket.MakeDecodeM[R, E, A0, A, B] =
    new Socket.MakeDecodeM[R, E, A0, A, B](self)

  def asResponse(
    subProtocol: Option[String] = None,
  )(implicit ev: IsResponse[R, E, A, B]): ZIO[R, Nothing, UResponse] = {
    Socket.asResponse(subProtocol)(ev(self))
  }

  def apply(A: A): ZStream[R, E, B] = asStream(A)

  def provide[R1 <: R](r: R1): Socket[Any, E, A, B] = Socket(a => self(a).provide(r))

  // TODO: handle [[Nothing]] separately
  def ignoreUnknowns(implicit ev: E <:< SocketError): Socket[R, Nothing, A, B] =
    Socket(a => self(a).catchAll(_ => ZStream.empty))

}

object Socket {

  def empty: Socket[Any, Nothing, Any, Nothing] = Socket(_ => ZStream.empty)

  def succeed[B](ws: B): Socket[Any, Nothing, Any, B] = Socket(_ => ZStream.succeed(ws))

  def fail[E](cause: E): Socket[Any, E, Any, Nothing] = Socket(_ => ZStream.fail(cause))

  def unknown: Socket[Any, SocketError, Any, Nothing] = fail(SocketError.UnknownMessage)

  def fromEffect[R, E, A, B](zio: ZIO[R, E, Socket[R, E, A, B]]): Socket[R, E, A, B] =
    Socket(a => ZStream.unwrap(zio.map(s => s(a))))

  def forsome[A] = new MakeForsomeSocket[A](())

  def forall[A] = new MakeForallSocket[A](())

  final class MakeForsomeSocket[A](val u: Unit) extends AnyVal {
    def apply[R, E >: SocketError, B](pf: PartialFunction[A, ZStream[R, E, B]]): Socket[R, E, A, B] =
      Socket(ws => if (pf.isDefinedAt(ws)) pf(ws) else ZStream.fail(SocketError.unknown))
  }

  final class MakeForallSocket[A](val u: Unit) extends AnyVal {
    def apply[R, E, B](f: A => ZStream[R, E, B]): Socket[R, E, A, B] = Socket(f)
  }

  final class MakeDecodeM[-R, +E, A0, -A, +B](val self: Socket[R, E, A, B]) extends AnyVal {
    def apply[R1 <: R, E1 >: E, A1 <: A](f: A0 => ZIO[R1, E1, A1]): Socket[R1, E1, A0, B] =
      Socket(ws => ZStream.fromEffect(f(ws)) >>= self.asStream)
  }

  def asResponse[R, E](
    subProtocol: Option[String],
  )(socket: Socket[R, E, WebSocketFrame, WebSocketFrame]): ZIO[R, Nothing, UResponse] =
    ZIO.environment[R] map { env =>
      Response.socket(subProtocol) { ws =>
        socket(ws).provide(env).catchAll(_ => ZStream.empty)
      }
    }
}
