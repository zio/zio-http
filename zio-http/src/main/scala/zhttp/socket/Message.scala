package zhttp.socket

import zio.stream.ZStream
import zio.Cause

sealed trait Message[-R, +E, -A, +B] { self =>
  def apply(a: A): ZStream[R, E, B] = Message.asStream(a, self)

  def map[C](bc: B => C): Message[R, E, A, C] = Message.FMap(self, bc)

  def cmap[Z](za: Z => A): Message[R, E, Z, B] = Message.FCMap(self, za)

  def <>[R1 <: R, E1 >: E, A1 <: A, B1 >: B](other: Message[R1, E1, A1, B1]): Message[R1, E1, A1, B1] =
    Message.FOrElse(self, other)

  def merge[R1 <: R, E1 >: E, A1 <: A, B1 >: B](other: Message[R1, E1, A1, B1]): Message[R1, E1, A1, B1] =
    Message.FMerge(self, other)
}

object Message {
  private final case class FromStreamingFunction[R, E, A, B](func: A => ZStream[R, E, B]) extends Message[R, E, A, B]
  private final case class FromStream[R, E, B](stream: ZStream[R, E, B])                  extends Message[R, E, Any, B]
  private final case class Succeed[A](a: A)                                               extends Message[Any, Nothing, Any, A]
  private final case class FMap[R, E, A, B, C](m: Message[R, E, A, B], bc: B => C)        extends Message[R, E, A, C]
  private final case class FCMap[R, E, X, A, B](m: Message[R, E, A, B], xa: X => A)       extends Message[R, E, X, B]
  private final case object End                                                           extends Message[Any, Nothing, Any, Nothing]
  private final case class FOrElse[R, E, A, B](a: Message[R, E, A, B], b: Message[R, E, A, B])
      extends Message[R, E, A, B]

  private final case class FMerge[R, E, A, B](a: Message[R, E, A, B], b: Message[R, E, A, B])
      extends Message[R, E, A, B]

  def collect[A]: MkCollect[A] = new MkCollect[A](())

  def succeed[A](a: A): Message[Any, Nothing, Any, A] = Succeed(a)

  def end: ZStream[Any, Nothing, Nothing] = ZStream.halt(Cause.empty)

  final class MkCollect[A](val unit: Unit) extends AnyVal {
    def apply[R, E, B](pf: PartialFunction[A, ZStream[R, E, B]]): Message[R, E, A, B] = Message.FromStreamingFunction {
      a =>
        if (pf.isDefinedAt(a)) pf(a) else ZStream.empty
    }
  }

  def asStream[R, E, A, B](a: A, message: Message[R, E, A, B]): ZStream[R, E, B] = {
    message match {
      case End                         => ZStream.halt(Cause.empty)
      case FromStreamingFunction(func) => func(a)
      case FromStream(s)               => s
      case FMap(m, bc)                 => m(a).map(bc)
      case FCMap(m, xa)                => m(xa(a))
      case FOrElse(sa, sb)             => sa(a) <> sb(a)
      case FMerge(sa, sb)              => sa(a) merge sb(a)
      case Succeed(a)                  => ZStream.succeed(a)
    }
  }
}
