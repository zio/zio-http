package zhttp.socket

import zio.stream.ZStream
import zio.{Cause, ZIO}

sealed trait Socket[-R, +E, -A, +B] { self =>
  import Socket._
  def apply(a: A): ZStream[R, E, B] = self match {
    case End                         => ZStream.halt(Cause.empty)
    case FromStreamingFunction(func) => func(a)
    case FromStream(s)               => s
    case FMap(m, bc)                 => m(a).map(bc)
    case FMapM(m, bc)                => m(a).mapM(bc)
    case FCMap(m, xa)                => m(xa(a))
    case FCMapM(m, xa)               => ZStream.fromEffect(xa(a)).flatMap(a => m(a))
    case FOrElse(sa, sb)             => sa(a) <> sb(a)
    case FMerge(sa, sb)              => sa(a) merge sb(a)
    case Succeed(a)                  => ZStream.succeed(a)
  }

  private[zhttp] def execute(a: A): ZStream[R, E, B] = self(a)

  def map[C](bc: B => C): Socket[R, E, A, C] = Socket.FMap(self, bc)

  def mapM[R1 <: R, E1 >: E, C](bc: B => ZIO[R1, E1, C]): Socket[R1, E1, A, C] = Socket.FMapM(self, bc)

  def contramap[Z](za: Z => A): Socket[R, E, Z, B] = Socket.FCMap(self, za)

  def contramapM[R1 <: R, E1 >: E, Z](za: Z => ZIO[R1, E1, A]): Socket[R1, E1, Z, B] = Socket.FCMapM(self, za)

  def <>[R1 <: R, E1, A1 <: A, B1 >: B](other: Socket[R1, E1, A1, B1]): Socket[R1, E1, A1, B1] =
    self orElse other

  def orElse[R1 <: R, E1, A1 <: A, B1 >: B](other: Socket[R1, E1, A1, B1]): Socket[R1, E1, A1, B1] =
    Socket.FOrElse(self, other)

  def merge[R1 <: R, E1 >: E, A1 <: A, B1 >: B](other: Socket[R1, E1, A1, B1]): Socket[R1, E1, A1, B1] =
    Socket.FMerge(self, other)
}

object Socket {
  private final case class FromStreamingFunction[R, E, A, B](func: A => ZStream[R, E, B])     extends Socket[R, E, A, B]
  private final case class FromStream[R, E, B](stream: ZStream[R, E, B])                      extends Socket[R, E, Any, B]
  private final case class Succeed[A](a: A)                                                   extends Socket[Any, Nothing, Any, A]
  private final case class FMap[R, E, A, B, C](m: Socket[R, E, A, B], bc: B => C)             extends Socket[R, E, A, C]
  private final case class FMapM[R, E, A, B, C](m: Socket[R, E, A, B], bc: B => ZIO[R, E, C]) extends Socket[R, E, A, C]
  private final case class FCMap[R, E, X, A, B](m: Socket[R, E, A, B], xa: X => A)            extends Socket[R, E, X, B]
  private final case class FCMapM[R, E, X, A, B](m: Socket[R, E, A, B], xa: X => ZIO[R, E, A])
      extends Socket[R, E, X, B]
  private case object End                                                                     extends Socket[Any, Nothing, Any, Nothing]
  private final case class FOrElse[R, E, E1, A, B](a: Socket[R, E, A, B], b: Socket[R, E1, A, B])
      extends Socket[R, E1, A, B]

  private final case class FMerge[R, E, A, B](a: Socket[R, E, A, B], b: Socket[R, E, A, B]) extends Socket[R, E, A, B]

  def collect[A]: MkCollect[A] = new MkCollect[A](())

  def succeed[A](a: A): Socket[Any, Nothing, Any, A] = Succeed(a)

  def fromStream[R, E, B](stream: ZStream[R, E, B]): Socket[R, E, Any, B] = FromStream(stream)

  def end: ZStream[Any, Nothing, Nothing] = ZStream.halt(Cause.empty)

  def fromFunction[A]: MkFromFunction[A] = new MkFromFunction[A](())

  final class MkFromFunction[A](val unit: Unit) extends AnyVal {
    def apply[R, E, B](f: A => ZStream[R, E, B]): Socket[R, E, A, B] = FromStreamingFunction(f)
  }

  final class MkCollect[A](val unit: Unit) extends AnyVal {
    def apply[R, E, B](pf: PartialFunction[A, ZStream[R, E, B]]): Socket[R, E, A, B] = Socket.FromStreamingFunction {
      a =>
        if (pf.isDefinedAt(a)) pf(a) else ZStream.empty
    }
  }
}
