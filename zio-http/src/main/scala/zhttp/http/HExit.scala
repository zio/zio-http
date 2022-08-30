package zhttp.http

import zhttp.http.HExit.Effect
import zio.ZIO

/**
 * Every `HttpApp` evaluates to an `HExit`. This domain is needed for improved
 * performance. This ensures that a `ZIO` effect is created only when it is
 * required. `HExit.Effect` wraps a ZIO effect, otherwise `HExits` are evaluated
 * without `ZIO`
 */
sealed trait HExit[-R, +E, +A] { self =>

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
    self.foldExit(HExit.fail, HExit.die, HExit.succeed, other)

  def flatMap[R1 <: R, E1 >: E, B](ab: A => HExit[R1, E1, B]): HExit[R1, E1, B] =
    self.foldExit(HExit.fail, HExit.die, ab, HExit.empty)

  def flatten[R1 <: R, E1 >: E, A1](implicit ev: A <:< HExit[R1, E1, A1]): HExit[R1, E1, A1] =
    self.flatMap(identity(_))

  def foldExit[R1 <: R, E1, B1](
    failure: E => HExit[R1, E1, B1],
    defect: Throwable => HExit[R1, E1, B1],
    success: A => HExit[R1, E1, B1],
    empty: HExit[R1, E1, B1],
  ): HExit[R1, E1, B1] =
    self match {
      case HExit.Success(a)  => success(a)
      case HExit.Failure(e)  => failure(e)
      case HExit.Die(t)      => defect(t)
      case HExit.Effect(zio) =>
        Effect(
          zio.foldCauseZIO(
            cause =>
              cause.failureOrCause match {
                case Left(Some(error)) => failure(error).toZIO
                case Left(None)        => empty.toZIO
                case Right(other)      =>
                  other.dieOption match {
                    case Some(t) => defect(t).toZIO
                    case None    => ZIO.failCause(other)
                  }
              },
            a => success(a).toZIO,
          ),
        )
      case HExit.Empty       => empty
    }

  def map[B](ab: A => B): HExit[R, E, B] = self.flatMap(a => HExit.succeed(ab(a)))

  def orElse[R1 <: R, E1, A1 >: A](other: HExit[R1, E1, A1]): HExit[R1, E1, A1] =
    self.foldExit(_ => other, HExit.die, HExit.succeed, HExit.empty)

  def toZIO: ZIO[R, Option[E], A] = self match {
    case HExit.Success(a)  => ZIO.succeed(a)
    case HExit.Failure(e)  => ZIO.fail(Option(e))
    case HExit.Die(e)      => ZIO.die(e)
    case HExit.Empty       => ZIO.fail(None)
    case HExit.Effect(zio) => zio
  }
}

object HExit {
  def die(t: Throwable): HExit[Any, Nothing, Nothing] = Die(t)

  def empty: HExit[Any, Nothing, Nothing] = Empty

  def fail[E](e: E): HExit[Any, E, Nothing] = Failure(e)

  def fromZIO[R, E, A](z: ZIO[R, E, A]): HExit[R, E, A] = Effect(z.mapError(Option(_)))

  def succeed[A](a: A): HExit[Any, Nothing, A] = Success(a)

  def unit: HExit[Any, Nothing, Unit] = HExit.succeed(())

  final case class Success[A](a: A) extends HExit[Any, Nothing, A]

  final case class Failure[E](e: E) extends HExit[Any, E, Nothing]

  final case class Die(t: Throwable) extends HExit[Any, Nothing, Nothing]

  final case class Effect[R, E, A](z: ZIO[R, Option[E], A]) extends HExit[R, E, A]

  case object Empty extends HExit[Any, Nothing, Nothing]
}
