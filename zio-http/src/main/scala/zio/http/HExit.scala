package zio.http

import zio.http.HExit.Effect
import zio.{Cause, Trace, ZIO}
import zio.stacktracer.TracingImplicits.disableAutoTrace // scalafix:ok;

/**
 * Every `HttpApp` evaluates to an `HExit`. This domain is needed for improved
 * performance. This ensures that a `ZIO` effect is created only when it is
 * required. `HExit.Effect` wraps a ZIO effect, otherwise `HExits` are evaluated
 * without `ZIO`
 */
sealed trait HExit[-R, +E, +A] { self =>

  def >>=[R1 <: R, E1 >: E, B](ab: A => HExit[R1, E1, B])(implicit trace: Trace): HExit[R1, E1, B] =
    self.flatMap(ab)

  def <>[R1 <: R, E1, A1 >: A](other: HExit[R1, E1, A1])(implicit trace: Trace): HExit[R1, E1, A1] =
    self orElse other

  def <+>[R1 <: R, E1 >: E, A1 >: A](other: HExit[R1, E1, A1])(implicit trace: Trace): HExit[R1, E1, A1] =
    this defaultWith other

  def *>[R1 <: R, E1 >: E, B](other: HExit[R1, E1, B])(implicit trace: Trace): HExit[R1, E1, B] =
    self.flatMap(_ => other)

  def as[B](b: B)(implicit trace: Trace): HExit[R, E, B] = self.map(_ => b)

  def defaultWith[R1 <: R, E1 >: E, A1 >: A](other: HExit[R1, E1, A1])(implicit trace: Trace): HExit[R1, E1, A1] =
    self.foldExit(HExit.failCause, HExit.succeed, other)

  def flatMap[R1 <: R, E1 >: E, B](ab: A => HExit[R1, E1, B])(implicit trace: Trace): HExit[R1, E1, B] =
    self.foldExit(HExit.failCause, ab, HExit.empty)

  def flatten[R1 <: R, E1 >: E, A1](implicit ev: A <:< HExit[R1, E1, A1], trace: Trace): HExit[R1, E1, A1] =
    self.flatMap(identity(_))

  def foldExit[R1 <: R, E1, B1](
    failure: Cause[E] => HExit[R1, E1, B1],
    success: A => HExit[R1, E1, B1],
    empty: HExit[R1, E1, B1],
  )(implicit trace: Trace): HExit[R1, E1, B1] =
    self match {
      case HExit.Success(a)     => success(a)
      case HExit.Failure(cause) => failure(cause)
      case HExit.Effect(zio)    =>
        Effect(
          zio.foldCauseZIO(
            cause =>
              cause.failureOrCause match {
                case Left(Some(error)) => failure(Cause.fail(error)).toZIO
                case Left(None)        => empty.toZIO
                case Right(other)      =>
                  other.dieOption match {
                    case Some(t) => failure(Cause.die(t)).toZIO
                    case None    => ZIO.failCause(other)
                  }
              },
            a => success(a).toZIO,
          ),
        )
      case HExit.Empty          => empty
    }

  def map[B](ab: A => B)(implicit trace: Trace): HExit[R, E, B] = self.flatMap(a => HExit.succeed(ab(a)))

  def orElse[R1 <: R, E1, A1 >: A](other: HExit[R1, E1, A1])(implicit trace: Trace): HExit[R1, E1, A1] =
    self.foldExit(_ => other, HExit.succeed, HExit.empty)

  def toZIO(implicit trace: Trace): ZIO[R, Option[E], A] = self match {
    case HExit.Success(a)  => ZIO.succeed(a)
    case HExit.Failure(e)  => ZIO.failCause(e.map(Some(_)))
    case HExit.Empty       => ZIO.fail(None)
    case HExit.Effect(zio) => zio
  }
}

object HExit {
  def die(t: Throwable): HExit[Any, Nothing, Nothing] = failCause(Cause.die(t))

  def empty: HExit[Any, Nothing, Nothing] = Empty

  def fail[E](e: E): HExit[Any, E, Nothing] = failCause(Cause.fail(e))

  def failCause[E](cause: Cause[E]): HExit[Any, E, Nothing] = Failure(cause)

  def fromZIO[R, E, A](z: ZIO[R, E, A])(implicit trace: Trace): HExit[R, E, A] = Effect(z.mapError(Option(_)))

  def succeed[A](a: A): HExit[Any, Nothing, A] = Success(a)

  def unit: HExit[Any, Nothing, Unit] = HExit.succeed(())

  final case class Success[A](a: A) extends HExit[Any, Nothing, A]

  final case class Failure[E](cause: Cause[E]) extends HExit[Any, E, Nothing]

  final case class Effect[R, E, A](z: ZIO[R, Option[E], A]) extends HExit[R, E, A]

  case object Empty extends HExit[Any, Nothing, Nothing]
}
