package zhttp.http

import zio.{CanFail, ZIO}

import scala.annotation.{tailrec, unused}

sealed trait HttpResult[-R, +E, +A] { self =>

  def map[B](ab: A => B): HttpResult[R, E, B] = self.flatMap(a => HttpResult.succeed(ab(a)))

  def as[B](b: B): HttpResult[R, E, B] = self.map(_ => b)

  def >>=[R1 <: R, E1 >: E, B](ab: A => HttpResult[R1, E1, B]): HttpResult[R1, E1, B] =
    self.flatMap(ab)

  def *>[R1 <: R, E1 >: E, B](other: HttpResult[R1, E1, B]): HttpResult[R1, E1, B] =
    self.flatMap(_ => other)

  def <>[R1 <: R, E1, A1 >: A](other: HttpResult[R1, E1, A1]): HttpResult[R1, E1, A1] =
    self orElse other

  def orElse[R1 <: R, E1, A1 >: A](other: HttpResult[R1, E1, A1]): HttpResult[R1, E1, A1] =
    self.flatMapError(_ => other)

  def flatMap[R1 <: R, E1 >: E, B](ab: A => HttpResult[R1, E1, B]): HttpResult[R1, E1, B] =
    HttpResult.flatMap(self, ab)

  def flatten[R1 <: R, E1 >: E, A1](implicit ev: A <:< HttpResult[R1, E1, A1]): HttpResult[R1, E1, A1] =
    self.flatMap(identity(_))

  def defaultWith[R1 <: R, E1 >: E, A1 >: A](other: HttpResult[R1, E1, A1]): HttpResult[R1, E1, A1] =
    self.foldM(HttpResult.fail, HttpResult.succeed, other)

  def <+>[R1 <: R, E1 >: E, A1 >: A](other: HttpResult[R1, E1, A1]): HttpResult[R1, E1, A1] =
    this defaultWith other

  def flatMapError[R1 <: R, E1, A1 >: A](h: E => HttpResult[R1, E1, A1])(implicit
    @unused ev: CanFail[E],
  ): HttpResult[R1, E1, A1] = HttpResult.flatMapError(self, h)

  def foldM[R1 <: R, E1, B1](
    ee: E => HttpResult[R1, E1, B1],
    aa: A => HttpResult[R1, E1, B1],
    dd: HttpResult[R1, E1, B1],
  ): HttpResult[R1, E1, B1] =
    HttpResult.foldM(self, ee, aa, dd)

  def fold[E1, B1](ee: E => E1, aa: A => B1): HttpResult[R, E1, B1] =
    self.foldM(e => HttpResult.fail(ee(e)), a => HttpResult.succeed(aa(a)), HttpResult.empty)

  def mapError[E1](ee: E => E1): HttpResult[R, E1, A]        =
    self.fold(ee, identity(_))

  final private[zhttp] def evaluate: HttpResult.Out[R, E, A] = HttpResult.evaluate(self)
}

object HttpResult {
  sealed trait Out[-R, +E, +A] extends HttpResult[R, E, A] { self =>
    def asEffect: ZIO[R, Option[E], A] = self match {
      case Empty      => ZIO.fail(None)
      case Success(a) => ZIO.succeed(a)
      case Failure(e) => ZIO.fail(Option(e))
      case Effect(z)  => z
    }
  }

  // CTOR
  final case class Success[A](a: A)                         extends Out[Any, Nothing, A]
  final case class Failure[E](e: E)                         extends Out[Any, E, Nothing]
  final case class Effect[R, E, A](z: ZIO[R, Option[E], A]) extends Out[R, E, A]
  case object Empty                                         extends Out[Any, Nothing, Nothing]

  // OPR
  private final case class EffectTotal[A](f: () => A)                     extends HttpResult[Any, Nothing, A]
  private final case class Suspend[R, E, A](r: () => HttpResult[R, E, A]) extends HttpResult[R, E, A]
  private final case class FoldM[R, E, EE, A, AA](
    rr: HttpResult[R, E, A],
    ee: E => HttpResult[R, EE, AA],
    aa: A => HttpResult[R, EE, AA],
    dd: HttpResult[R, EE, AA],
  ) extends HttpResult[R, EE, AA]

  // Help
  def succeed[A](a: A): HttpResult.Out[Any, Nothing, A] = Success(a)
  def fail[E](e: E): HttpResult.Out[Any, E, Nothing]    = Failure(e)
  def empty: HttpResult.Out[Any, Nothing, Nothing]      = Empty

  def effect[R, E, A](z: ZIO[R, E, A]): HttpResult.Out[R, E, A] = Effect(z.mapError(Option(_)))
  def effectTotal[A](z: => A): HttpResult[Any, Nothing, A]      = EffectTotal(() => z)
  def unit: HttpResult[Any, Nothing, Unit]                      = HttpResult.succeed(())

  def flatMap[R, E, A, B](r: HttpResult[R, E, A], aa: A => HttpResult[R, E, B]): HttpResult[R, E, B] =
    HttpResult.foldM(r, HttpResult.fail[E], aa, HttpResult.empty)

  def suspend[R, E, A](r: => HttpResult[R, E, A]): HttpResult[R, E, A] = HttpResult.Suspend(() => r)

  def foldM[R, E, EE, A, AA](
    r: HttpResult[R, E, A],
    ee: E => HttpResult[R, EE, AA],
    aa: A => HttpResult[R, EE, AA],
    dd: HttpResult[R, EE, AA],
  ): HttpResult[R, EE, AA] =
    HttpResult.FoldM(r, ee, aa, dd)

  def flatMapError[R, E, E1, A](r: HttpResult[R, E, A], ee: E => HttpResult[R, E1, A]): HttpResult[R, E1, A] =
    HttpResult.foldM(r, ee, HttpResult.succeed[A], HttpResult.empty)

  // EVAL
  @tailrec
  private[zhttp] def evaluate[R, E, A](result: HttpResult[R, E, A]): Out[R, E, A] = {
    result match {
      case m: Out[_, _, _]         => m
      case Suspend(r)              => evaluate(r())
      case EffectTotal(f)          => HttpResult.succeed(f())
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
