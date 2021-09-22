package zhttp.http

import zio.{CanFail, ZIO}

import scala.annotation.{tailrec, unused}

private[zhttp] sealed trait HExit[-R, +E, +A] { self =>

  def map[B](ab: A => B): HExit[R, E, B] = self.flatMap(a => HExit.succeed(ab(a)))

  def as[B](b: B): HExit[R, E, B] = self.map(_ => b)

  def >>=[R1 <: R, E1 >: E, B](ab: A => HExit[R1, E1, B]): HExit[R1, E1, B] =
    self.flatMap(ab)

  def *>[R1 <: R, E1 >: E, B](other: HExit[R1, E1, B]): HExit[R1, E1, B] =
    self.flatMap(_ => other)

  def <>[R1 <: R, E1, A1 >: A](other: HExit[R1, E1, A1]): HExit[R1, E1, A1] =
    self orElse other

  def orElse[R1 <: R, E1, A1 >: A](other: HExit[R1, E1, A1]): HExit[R1, E1, A1] =
    self.flatMapError(_ => other)

  def flatMap[R1 <: R, E1 >: E, B](ab: A => HExit[R1, E1, B]): HExit[R1, E1, B] =
    HExit.flatMap(self, ab)

  def flatten[R1 <: R, E1 >: E, A1](implicit ev: A <:< HExit[R1, E1, A1]): HExit[R1, E1, A1] =
    self.flatMap(identity(_))

  def defaultWith[R1 <: R, E1 >: E, A1 >: A](other: HExit[R1, E1, A1]): HExit[R1, E1, A1] =
    self.foldM(HExit.fail, HExit.succeed, other)

  def <+>[R1 <: R, E1 >: E, A1 >: A](other: HExit[R1, E1, A1]): HExit[R1, E1, A1] =
    this defaultWith other

  def flatMapError[R1 <: R, E1, A1 >: A](h: E => HExit[R1, E1, A1])(implicit
    @unused ev: CanFail[E],
  ): HExit[R1, E1, A1] = HExit.flatMapError(self, h)

  def foldM[R1 <: R, E1, B1](
    ee: E => HExit[R1, E1, B1],
    aa: A => HExit[R1, E1, B1],
    dd: HExit[R1, E1, B1],
  ): HExit[R1, E1, B1] =
    HExit.foldM(self, ee, aa, dd)

  def fold[E1, B1](ee: E => E1, aa: A => B1): HExit[R, E1, B1] =
    self.foldM(e => HExit.fail(ee(e)), a => HExit.succeed(aa(a)), HExit.empty)

  def mapError[E1](ee: E => E1): HExit[R, E1, A] =
    self.fold(ee, identity(_))

  final private[zhttp] def evaluate: HExit.Out[R, E, A] = HExit.evaluate(self)
}

object HExit {
  sealed trait Out[-R, +E, +A] extends HExit[R, E, A] { self =>
    def asEffect: ZIO[R, Option[E], A] = self match {
      case Empty      => ZIO.fail(None)
      case Success(a) => ZIO.succeed(a)
      case Failure(e) => ZIO.fail(Option(e))
      case Effect(z)  => z
    }

    def isEmpty: Boolean = self match {
      case HExit.Empty => true
      case _           => false
    }
  }

  // CTOR
  final case class Success[A](a: A)                         extends Out[Any, Nothing, A]
  final case class Failure[E](e: E)                         extends Out[Any, E, Nothing]
  final case class Effect[R, E, A](z: ZIO[R, Option[E], A]) extends Out[R, E, A]
  case object Empty                                         extends Out[Any, Nothing, Nothing]

  // OPR
  private final case class EffectTotal[A](f: () => A)                extends HExit[Any, Nothing, A]
  private final case class Suspend[R, E, A](r: () => HExit[R, E, A]) extends HExit[R, E, A]
  private final case class FoldM[R, E, EE, A, AA](
    rr: HExit[R, E, A],
    ee: E => HExit[R, EE, AA],
    aa: A => HExit[R, EE, AA],
    dd: HExit[R, EE, AA],
  )                                                                  extends HExit[R, EE, AA]

  // Help
  def succeed[A](a: A): HExit.Out[Any, Nothing, A] = Success(a)
  def fail[E](e: E): HExit.Out[Any, E, Nothing]    = Failure(e)
  def empty: HExit.Out[Any, Nothing, Nothing]      = Empty

  def effect[R, E, A](z: ZIO[R, E, A]): HExit.Out[R, E, A] = Effect(z.mapError(Option(_)))
  def effectTotal[A](z: => A): HExit[Any, Nothing, A]      = EffectTotal(() => z)
  def unit: HExit[Any, Nothing, Unit]                      = HExit.succeed(())

  def flatMap[R, E, A, B](r: HExit[R, E, A], aa: A => HExit[R, E, B]): HExit[R, E, B] =
    HExit.foldM(r, HExit.fail[E], aa, HExit.empty)

  def suspend[R, E, A](r: => HExit[R, E, A]): HExit[R, E, A] = HExit.Suspend(() => r)

  def foldM[R, E, EE, A, AA](
    r: HExit[R, E, A],
    ee: E => HExit[R, EE, AA],
    aa: A => HExit[R, EE, AA],
    dd: HExit[R, EE, AA],
  ): HExit[R, EE, AA] =
    HExit.FoldM(r, ee, aa, dd)

  def flatMapError[R, E, E1, A](r: HExit[R, E, A], ee: E => HExit[R, E1, A]): HExit[R, E1, A] =
    HExit.foldM(r, ee, HExit.succeed[A], HExit.empty)

  // EVAL
  @tailrec
  private[zhttp] def evaluate[R, E, A](result: HExit[R, E, A]): Out[R, E, A] = {
    result match {
      case m: Out[_, _, _]         => m
      case Suspend(r)              => evaluate(r())
      case EffectTotal(f)          => HExit.succeed(f())
      case FoldM(self, ee, aa, dd) =>
        evaluate(self match {
          case Empty                      => dd
          case Success(a)                 => aa(a)
          case Failure(e)                 => ee(e)
          case Suspend(r)                 => r().foldM(ee, aa, dd)
          case EffectTotal(f)             => aa(f())
          case Effect(z)                  =>
            Effect(
              z.foldM(
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
