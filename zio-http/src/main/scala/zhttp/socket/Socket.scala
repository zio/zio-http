package zhttp.socket

import zhttp.http.{Http, Response}
import zhttp.service.{ChannelFactory, EventLoopGroup}
import zio.clock.Clock
import zio.duration.Duration
import zio.stream.ZStream
import zio.{Cause, NeedsEnv, ZIO, ZManaged}

sealed trait Socket[-R, +E, -A, +B] { self =>
  import Socket._
  private[zhttp] def execute(a: A): ZStream[R, E, B] = self(a)

  def <>[R1 <: R, E1, A1 <: A, B1 >: B](other: Socket[R1, E1, A1, B1]): Socket[R1, E1, A1, B1] =
    self orElse other

  def apply(a: A): ZStream[R, E, B] = self match {
    case End                         => ZStream.halt(Cause.empty)
    case FromStreamingFunction(func) => func(a)
    case FromStream(s)               => s
    case FMap(m, bc)                 => m(a).map(bc)
    case FMapZIO(m, bc)              => m(a).mapM(bc)
    case FCMap(m, xa)                => m(xa(a))
    case FCMapZIO(m, xa)             => ZStream.fromEffect(xa(a)).flatMap(a => m(a))
    case FOrElse(sa, sb)             => sa(a) <> sb(a)
    case FMerge(sa, sb)              => sa(a) merge sb(a)
    case Succeed(a)                  => ZStream.succeed(a)
    case ProvideEnvironment(s, r)    => s(a).asInstanceOf[ZStream[R, E, B]].provide(r.asInstanceOf[R])
    case Empty                       => ZStream.empty
  }

  def connect(url: String)(implicit
    ev: IsWebSocket[R, E, A, B],
  ): ZManaged[R with EventLoopGroup with ChannelFactory, Throwable, Response] =
    self.toSocketApp.connect(url)

  def contramap[Z](za: Z => A): Socket[R, E, Z, B] = Socket.FCMap(self, za)

  def contramapZIO[R1 <: R, E1 >: E, Z](za: Z => ZIO[R1, E1, A]): Socket[R1, E1, Z, B] = Socket.FCMapZIO(self, za)

  /**
   * Delays delivery of messages by the specified duration.
   */
  def delay(duration: Duration): Socket[Clock with R, E, A, B] = self.tap(_ => ZIO.sleep(duration))

  def map[C](bc: B => C): Socket[R, E, A, C] = Socket.FMap(self, bc)

  def mapZIO[R1 <: R, E1 >: E, C](bc: B => ZIO[R1, E1, C]): Socket[R1, E1, A, C] = Socket.FMapZIO(self, bc)

  def merge[R1 <: R, E1 >: E, A1 <: A, B1 >: B](other: Socket[R1, E1, A1, B1]): Socket[R1, E1, A1, B1] =
    Socket.FMerge(self, other)

  def orElse[R1 <: R, E1, A1 <: A, B1 >: B](other: Socket[R1, E1, A1, B1]): Socket[R1, E1, A1, B1] =
    Socket.FOrElse(self, other)

  /**
   * Provides the socket with its required environment, which eliminates its
   * dependency on R. This operation assumes that your socket requires an
   * environment.
   */
  def provideEnvironment(r: R)(implicit env: NeedsEnv[R]): Socket[Any, E, A, B] = ProvideEnvironment(self, r)

  /**
   * Executes the effect for each message received from the socket, and ignores
   * the output produced.
   */
  def tap[R1 <: R, E1 >: E](f: B => ZIO[R1, E1, Any]): Socket[R1, E1, A, B] = self.mapZIO(b => f(b).as(b))

  /**
   * Converts the Socket into an Http
   */
  def toHttp(implicit ev: IsWebSocket[R, E, A, B]): Http[R, E, Any, Response] = Http.fromZIO(toResponse)

  /**
   * Creates a response from the socket.
   */
  def toResponse(implicit ev: IsWebSocket[R, E, A, B]): ZIO[R, Nothing, Response] = toSocketApp.toResponse

  /**
   * Creates a socket application from the socket.
   */
  def toSocketApp(implicit ev: IsWebSocket[R, E, A, B]): SocketApp[R] = SocketApp(self)
}

object Socket {

  def collect[A]: PartialCollect[A] = new PartialCollect[A](())

  /**
   * Simply echos the incoming message back
   */
  def echo[A]: Socket[Any, Nothing, A, A] = Socket.collect[A] { case a => ZStream.succeed(a) }

  /**
   * Creates a socket that doesn't do anything.
   */
  def empty: Socket[Any, Nothing, Any, Nothing] = Socket.Empty

  def end: Socket[Any, Nothing, Any, Nothing] = Socket.End

  def from[A](iter: A*): Socket[Any, Nothing, Any, A] = fromIterable(iter)

  def fromFunction[A]: PartialFromFunction[A] = new PartialFromFunction[A](())

  def fromIterable[A](iter: Iterable[A]): Socket[Any, Nothing, Any, A] = Socket.fromStream(ZStream.fromIterable(iter))

  def fromStream[R, E, B](stream: ZStream[R, E, B]): Socket[R, E, Any, B] = FromStream(stream)

  def succeed[A](a: A): Socket[Any, Nothing, Any, A] = Succeed(a)

  final class PartialFromFunction[A](val unit: Unit) extends AnyVal {
    def apply[R, E, B](f: A => ZStream[R, E, B]): Socket[R, E, A, B] = FromStreamingFunction(f)
  }

  final class PartialCollect[A](val unit: Unit) extends AnyVal {
    def apply[R, E, B](pf: PartialFunction[A, ZStream[R, E, B]]): Socket[R, E, A, B] = Socket.FromStreamingFunction {
      a =>
        if (pf.isDefinedAt(a)) pf(a) else ZStream.empty
    }
  }

  private final case class FromStreamingFunction[R, E, A, B](func: A => ZStream[R, E, B]) extends Socket[R, E, A, B]

  private final case class FromStream[R, E, B](stream: ZStream[R, E, B]) extends Socket[R, E, Any, B]

  private final case class Succeed[A](a: A) extends Socket[Any, Nothing, Any, A]

  private final case class FMap[R, E, A, B, C](m: Socket[R, E, A, B], bc: B => C) extends Socket[R, E, A, C]

  private final case class FMapZIO[R, E, A, B, C](m: Socket[R, E, A, B], bc: B => ZIO[R, E, C])
      extends Socket[R, E, A, C]

  private final case class FCMap[R, E, X, A, B](m: Socket[R, E, A, B], xa: X => A) extends Socket[R, E, X, B]

  private final case class FCMapZIO[R, E, X, A, B](m: Socket[R, E, A, B], xa: X => ZIO[R, E, A])
      extends Socket[R, E, X, B]

  private final case class FOrElse[R, E, E1, A, B](a: Socket[R, E, A, B], b: Socket[R, E1, A, B])
      extends Socket[R, E1, A, B]

  private final case class FMerge[R, E, A, B](a: Socket[R, E, A, B], b: Socket[R, E, A, B]) extends Socket[R, E, A, B]

  private final case class ProvideEnvironment[R, E, A, B](s: Socket[R, E, A, B], r: R) extends Socket[Any, E, A, B]

  private case object End extends Socket[Any, Nothing, Any, Nothing]

  private case object Empty extends Socket[Any, Nothing, Any, Nothing]
}
