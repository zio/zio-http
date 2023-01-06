package zio.http

import zio.http.HExit.Effect
import zio.{Cause, Tag, Trace, ZEnvironment, ZIO, ZLayer}
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

  def *>[R1 <: R, E1 >: E, B](other: HExit[R1, E1, B])(implicit trace: Trace): HExit[R1, E1, B] =
    self.flatMap(_ => other)

  def as[B](b: B)(implicit trace: Trace): HExit[R, E, B] = self.map(_ => b)

  def flatMap[R1 <: R, E1 >: E, B](ab: A => HExit[R1, E1, B])(implicit trace: Trace): HExit[R1, E1, B] =
    self.foldExit(HExit.failCause, ab)

  def flatten[R1 <: R, E1 >: E, A1](implicit ev: A <:< HExit[R1, E1, A1], trace: Trace): HExit[R1, E1, A1] =
    self.flatMap(identity(_))

  def foldExit[R1 <: R, E1, B1](
    failure: Cause[E] => HExit[R1, E1, B1],
    success: A => HExit[R1, E1, B1],
  )(implicit trace: Trace): HExit[R1, E1, B1] =
    self match {
      case HExit.Success(a)     => success(a)
      case HExit.Failure(cause) => failure(cause)
      case HExit.Effect(zio)    =>
        Effect(
          zio.foldCauseZIO(
            cause =>
              cause.failureOrCause match {
                case Left(error)  => failure(Cause.fail(error)).toZIO
                case Right(other) =>
                  other.dieOption match {
                    case Some(t) => failure(Cause.die(t)).toZIO
                    case None    => ZIO.failCause(other)
                  }
              },
            a => success(a).toZIO,
          ),
        )
    }

  def map[B](ab: A => B)(implicit trace: Trace): HExit[R, E, B] = self.flatMap(a => HExit.succeed(ab(a)))

  final def provideEnvironment(r: ZEnvironment[R])(implicit trace: Trace): HExit[Any, E, A] =
    self match {
      case HExit.Success(_) => self.asInstanceOf[HExit[Any, E, A]]
      case HExit.Failure(_) => self.asInstanceOf[HExit[Any, E, A]]
      case Effect(z)        => Effect(z.provideEnvironment(r))
    }

  final def provideLayer[E1 >: E, R0](layer: ZLayer[R0, E1, R])(implicit
    trace: Trace,
  ): HExit[R0, E1, A] =
    self match {
      case HExit.Success(_) => self.asInstanceOf[HExit[R0, E1, A]]
      case HExit.Failure(_) => self.asInstanceOf[HExit[R0, E1, A]]
      case Effect(z)        => Effect(z.provideLayer(layer))
    }

  final def provideSomeEnvironment[R1](f: ZEnvironment[R1] => ZEnvironment[R])(implicit
    trace: Trace,
  ): HExit[R1, E, A] =
    self match {
      case HExit.Success(_) => self.asInstanceOf[HExit[R1, E, A]]
      case HExit.Failure(_) => self.asInstanceOf[HExit[R1, E, A]]
      case Effect(z)        => Effect(z.provideSomeEnvironment[R1](f))
    }

  final def provideSomeLayer[R0, R1: Tag, E1 >: E](
    layer: ZLayer[R0, E1, R1],
  )(implicit ev: R0 with R1 <:< R, trace: Trace): HExit[R0, E1, A] =
    self match {
      case HExit.Success(_) => self.asInstanceOf[HExit[R0, E1, A]]
      case HExit.Failure(_) => self.asInstanceOf[HExit[R0, E1, A]]
      case Effect(z)        => Effect(z.provideSomeLayer(layer))
    }

  def orElse[R1 <: R, E1, A1 >: A](other: HExit[R1, E1, A1])(implicit trace: Trace): HExit[R1, E1, A1] =
    self.foldExit(_ => other, HExit.succeed)

  def toZIO(implicit trace: Trace): ZIO[R, E, A] = self match {
    case HExit.Success(a)  => ZIO.succeed(a)
    case HExit.Failure(e)  => ZIO.failCause(e)
    case HExit.Effect(zio) => zio
  }
}

object HExit {
  def die(t: Throwable): HExit[Any, Nothing, Nothing] = failCause(Cause.die(t))

  def fail[E](e: E): HExit[Any, E, Nothing] = failCause(Cause.fail(e))

  def failCause[E](cause: Cause[E]): HExit[Any, E, Nothing] = Failure(cause)

  def fromZIO[R, E, A](z: ZIO[R, E, A]): HExit[R, E, A] = Effect(z)

  def succeed[A](a: A): HExit[Any, Nothing, A] = Success(a)

  def unit: HExit[Any, Nothing, Unit] = HExit.succeed(())

  final case class Success[A](a: A) extends HExit[Any, Nothing, A]

  final case class Failure[E](cause: Cause[E]) extends HExit[Any, E, Nothing]

  final case class Effect[R, E, A](z: ZIO[R, E, A]) extends HExit[R, E, A]
}
