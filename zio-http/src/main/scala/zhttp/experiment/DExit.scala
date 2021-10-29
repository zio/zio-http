package zhttp.experiment

import zio.{CanFail, ZIO}

import scala.annotation.{tailrec, unused}

private[zhttp] sealed trait DExit[-R, +E, -A,+B] { self =>

  def map[C](bc: B => C): DExit[R, E, A, C] = self.flatMap((b:B) => DExit.succeed(bc(b)))

  def as[C](c: C): DExit[R, E, A,C] = self.map(_ => c)

  def >>=[R1 <: R, E1 >: E, B,C](ab: B => DExit[R1, E1, B,C]): DExit[R1, E1, A,C] =
    self.flatMap(ab)

  def *>[R1 <: R, E1 >: E, B1,A1<:A](other: DExit[R1, E1, A1,B1]): DExit[R1, E1, A1,B1] =
    self.flatMap((_:B) => other)

  def <>[R1 <: R, E1, A1 <: A, B1>:B](other: DExit[R1, E1, A1,B1]): DExit[R1, E1, A1,B1] =
    self orElse other

  def orElse[R1 <: R, E1, A1 <: A, B1>:B](other: DExit[R1, E1, A1, B1]): DExit[R1, E1, A1, B1] =
    self.flatMapError(_ => other)

  def flatMap[R1 <: R, E1 >: E, B,C](bc: B => DExit[R1, E1, B,C]): DExit[R1, E1, A,C] =
    DExit.flatMap(self, bc)

  def flatten[R1 <: R, E1 >: E, A1<:A,B1](implicit ev: B <:< DExit[R1, E1, A1,B1]): DExit[R1, E1, A1,B1] =
    self.flatMap(identity(_))

  def defaultWith[R1 <: R, E1 >: E, A1 <: A,B1>:B](other: DExit[R1, E1, A1,B1]): DExit[R1, E1, A1,B1] =
    self.foldM(DExit.fail, DExit.succeed, other)

  def <+>[R1 <: R, E1 >: E, A1 <: A,B1>:B](other: DExit[R1, E1, A1,B1]): DExit[R1, E1, A1,B1] =
    this defaultWith other

  def flatMapError[R1 <: R, E1, A1 <: A, B1>:B](h: E => DExit[R1, E1, A1,B1])(implicit
                                                                    @unused ev: CanFail[E],
  ): DExit[R1, E1, A1,B1] = DExit.flatMapError(self, h)

  def foldM[R1 <: R, E1,A1<:A, B1](
                              ee: E => DExit[R1, E1,A1, B1],
                              aa: B => DExit[R1, E1,A1, B1],
                              dd: DExit[R1, E1,A1, B1],
                            ): DExit[R1, E1, A1,B1] =
    DExit.foldM(self, ee, aa, dd)

  def fold[E1, C](ee: E => E1, aa: B => C): DExit[R, E1,A, C] =
    self.foldM(e => DExit.fail(ee(e)), b => DExit.succeed(aa(b)), DExit.empty)

  def mapError[E1](ee: E => E1): DExit[R, E1, A,B] =
    self.fold(ee, identity(_))

  final private[zhttp] def evaluate: DExit.DOut[R, E, A,B] = DExit.evaluate(self)
}

object DExit {
  sealed trait DOut[-R, +E, -A, +B] extends DExit[R, E, A,B] { self =>
    def asEffect: ZIO[R, Option[E], B] = self match {
      case Empty      => ZIO.fail(None)
      case Success(b) => ZIO.succeed(b)
      case Failure(e) => ZIO.fail(Option(e))
      case Effect(z)  => ???
    }

    def isEmpty: Boolean = self match {
      case DExit.Empty => true
      case _           => false
    }
  }

  // CTOR
  final case class Success[A](a: A)                         extends DOut[Any, Nothing, Any,A]
  final case class Failure[E](e: E)                         extends DOut[Any, E, Any,Nothing]
  final case class Effect[R, E,A, B](z: A=>ZIO[R, Option[E], B]) extends DOut[R, E,A, B]
  case object Empty                                         extends DOut[Any, Nothing, Any, Nothing]
  case class Step[R, E, S, A, B](state: S, next: (A, S, Boolean) => ZIO[R, E, (Option[B], S)])
    extends DExit[R, E, A, B]
  // OPR
  private final case class EffectTotal[B](f: () => B)                extends DExit[Any, Nothing, Any,B]
  private final case class Suspend[R, E, A,B](r: () => DExit[R, E, A,B]) extends DExit[R, E, A,B]
  private final case class FoldM[R, E, EE, A,B,BB](
                                                   rr: DExit[R, E, A,B],
                                                   ee: E => DExit[R, EE, A,BB],
                                                   aa: B => DExit[R, EE, B,BB],
                                                   dd: DExit[R, EE, A,BB],
                                                 )                                                                  extends DExit[R, EE, A,BB]

  // Help
  def succeed[B](b:B): DExit.DOut[Any, Nothing,Any, B] = Success(b)
  def fail[E](e: E): DExit.DOut[Any, E, Any,Nothing]    = Failure(e)
  def empty: DExit.DOut[Any, Nothing,Any, Nothing]      = Empty

  def effect[R, E, A,B](z: A=>ZIO[R, E, B]): DExit.DOut[R, E, A,B] = Effect(a=>z(a).mapError(Option(_)))
  def effectTotal[B](z: => B): DExit[Any, Nothing, Any, B]      = EffectTotal(() => z)
  def unit: DExit[Any, Nothing,Any, Unit]                      = DExit.succeed(())

  def flatMap[R, E, A, B,C](r: DExit[R, E, A,B], aa: B => DExit[R, E, B,C]): DExit[R, E, A,C] =
    DExit.foldM(r, DExit.fail[E], aa, DExit.empty)

  def suspend[R, E, A,B](r: => DExit[R, E, A,B]): DExit[R, E, A,B] = DExit.Suspend(() => r)

  def foldM[R, E, EE, A,B,BB](
                              rr: DExit[R, E, A,B],
                              ee: E => DExit[R, EE, A,BB],
                              aa: B => DExit[R, EE, B,BB],
                              dd: DExit[R, EE, A,BB],
                            ): DExit[R, EE,A, BB] =
    DExit.FoldM(rr, ee, aa, dd)

  def flatMapError[R, E, EE, A,B,BB](r: DExit[R, E, A,B], ee: E => DExit[R, EE, A,BB]): DExit[R, EE, A,BB] =
    DExit.foldM(r, ee, DExit.succeed[BB], DExit.empty)

  // EVAL
  @tailrec
  private[zhttp] def evaluate[R, E, A,B](result: DExit[R, E, A,B]): DOut[R, E, A,B] = {
    result match {
      case m: DOut[_, _, _,_]         => m
      case Suspend(r)              => evaluate(r())
      case EffectTotal(f)          => DExit.succeed(f())
      case FoldM(self, ee, aa, dd) =>
        evaluate(self match {
          case Empty                      => dd
          case Success(a)                 => aa(a)
          case Failure(e)                 => ee(e)
          case Suspend(r)                 => r().foldM(ee, aa, dd)
          case EffectTotal(f)             => aa(f())
          case Effect(z)                  =>
            Effect(
              a=>z(a).foldM(
                {
                  case None    => ZIO.fail(None)
                  case Some(e) => ee(e).evaluate.asEffect
                },
                aa(_).evaluate.asEffect,
              ),
            )
          case FoldM(self, ee0, aa0, dd0) =>
            self.foldM(
              e => ee0(e).foldM(ee, aa, dd),
              a => aa0(a).foldM(ee, aa, dd),
              dd0.foldM(ee, aa, dd),
            )
        })
    }
  }
}