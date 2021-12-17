package zhttp.http

import zhttp.http.HExit.Effect
import zio.ZIO

private[zhttp] sealed trait HExit[-R, +E, +A] { self =>

  def >>=[R1 <: R, E1 >: E, B](ab: A => HExit[R1, E1, B]): HExit[R1, E1, B] =
    self.flatMap(ab)

  def <>[R1 <: R, E1, A1 >: A](other: HExit[R1, E1, A1]): HExit[R1, E1, A1] =
    self orElse other

  def <+>[R1 <: R, E1 >: E, A1 >: A](other: HExit[R1, E1, A1]): HExit[R1, E1, A1] =
    this defaultWith other

  def *>[R1 <: R, E1 >: E, B](other: HExit[R1, E1, B]): HExit[R1, E1, B] =
    self.flatMap(_ => other)

  def as[B](b: B): HExit[R, E, B] = self.map(_ => b)

  def defaultWith[R1 <: R, E1 >: E, A1 >: A](other: HExit[R1, E1, A1]): HExit[R1, E1, A1] =
    self.foldM(HExit.fail, HExit.succeed, other)

  def flatMap[R1 <: R, E1 >: E, B](ab: A => HExit[R1, E1, B]): HExit[R1, E1, B] =
    self.foldM(HExit.fail, ab, HExit.empty)

  def flatten[R1 <: R, E1 >: E, A1](implicit ev: A <:< HExit[R1, E1, A1]): HExit[R1, E1, A1] =
    self.flatMap(identity(_))

  def foldM[R1 <: R, E1, B1](
    ee: E => HExit[R1, E1, B1],
    aa: A => HExit[R1, E1, B1],
    dd: HExit[R1, E1, B1],
  ): HExit[R1, E1, B1] =
    self match {
      case HExit.Success(a)  => aa(a)
      case HExit.Failure(e)  => ee(e)
      case HExit.Effect(zio) =>
        Effect(
          zio.foldM(
            {
              case Some(error) => ee(error).toEffect
              case None        => dd.toEffect
            },
            a => aa(a).toEffect,
          ),
        )
      case HExit.Empty       => dd
    }

  def map[B](ab: A => B): HExit[R, E, B] = self.flatMap(a => HExit.succeed(ab(a)))

  def orElse[R1 <: R, E1, A1 >: A](other: HExit[R1, E1, A1]): HExit[R1, E1, A1] =
    self.foldM(_ => other, HExit.succeed, HExit.empty)

  def toEffect: ZIO[R, Option[E], A] = self match {
    case HExit.Success(a)  => ZIO.succeed(a)
    case HExit.Failure(e)  => ZIO.fail(Option(e))
    case HExit.Empty       => ZIO.fail(None)
    case HExit.Effect(zio) => zio
  }
}

object HExit {
  def effect[R, E, A](z: ZIO[R, E, A]): HExit[R, E, A] = Effect(z.mapError(Option(_)))

  def empty: HExit[Any, Nothing, Nothing] = Empty

  def fail[E](e: E): HExit[Any, E, Nothing] = Failure(e)

  def succeed[A](a: A): HExit[Any, Nothing, A] = Success(a)

  def unit: HExit[Any, Nothing, Unit] = HExit.succeed(())

  final case class Success[A](a: A) extends HExit[Any, Nothing, A]

  final case class Failure[E](e: E) extends HExit[Any, E, Nothing]

  final case class Effect[R, E, A](z: ZIO[R, Option[E], A]) extends HExit[R, E, A]

  case object Empty extends HExit[Any, Nothing, Nothing]
}
