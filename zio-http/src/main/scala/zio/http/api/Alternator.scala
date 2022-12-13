package zio.http.api

import zio.stacktracer.TracingImplicits.disableAutoTrace // scalafix:ok;

/**
 * A alternator is a type class responsible for combining invariant type
 * parameters using an either. It is used to compose parameters of the
 * [[zio.http.api.HttpCodec]] data type.
 */
sealed trait Alternator[L, R] {
  type Out

  def left(l: L): Out

  def right(r: R): Out

  def unleft(out: Out): Option[L]

  def unright(out: Out): Option[R]
}

object Alternator extends AlternatorLowPriority1 {
  type WithOut[L, R, Out0] = Alternator[L, R] { type Out = Out0 }

  implicit def leftEmpty[A]: Alternator.WithOut[Unused, A, A] =
    (new Alternator[Any, A] {
      type Out = A

      def left(l: Any): Out = l.asInstanceOf[Out]

      def right(r: A): Out = r

      def unleft(out: Out): Option[Any] = None

      def unright(out: Out): Option[A] = Some(out)
    }).asInstanceOf[Alternator.WithOut[Unused, A, A]] // Work around compiler bug
}

trait AlternatorLowPriority1 extends AlternatorLowPriority2 {
  implicit def rightEmpty[A]: Alternator.WithOut[A, Unused, A] =
    (new Alternator[A, Any] {
      type Out = A

      def left(l: A): Out = l

      def right(r: Any): Out = r.asInstanceOf[Out]

      def unleft(out: Out): Option[A] = Some(out)

      def unright(out: Out): Option[Any] = None
    }).asInstanceOf[Alternator.WithOut[A, Unused, A]] // Work around compiler bug
}

trait AlternatorLowPriority2 {
  implicit def either[A, B]: Alternator.WithOut[A, B, Either[A, B]] =
    new Alternator[A, B] {
      type Out = Either[A, B]

      def left(l: A): Out = Left(l)

      def right(r: B): Out = Right(r)

      def unleft(out: Out): Option[A] = out.left.toOption

      def unright(out: Out): Option[B] = out.toOption
    }
}
