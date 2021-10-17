package zhttp.experiment

import zhttp.http.Http
import zio.ZIO

/**
 * Think if middlewares as a polymorphic function that is capable of transforming one http into another.
 */
sealed trait Middleware[-R, +E, +AIn, -BIn, -AOut, +BOut] { self =>
  def run[R1 <: R, E1 >: E](http: Http[R1, E1, AIn, BIn]): Http[R1, E1, AOut, BOut] = Middleware.execute(self, http)

  def apply[R1 <: R, E1 >: E](http: Http[R1, E1, AIn, BIn]): Http[R1, E1, AOut, BOut] = self.run(http)

  def >>>[R1 <: R, E1 >: E, A1In <: AOut, B1In >: BOut, A2Out, B2Out](
    other: Middleware[R1, E1, A1In, B1In, A2Out, B2Out],
  ): Middleware[R1, E1, AIn, BIn, A2Out, B2Out] =
    self andThen other

  def andThen[R1 <: R, E1 >: E, A1In <: AOut, B1In >: BOut, A2Out, B2Out](
    other: Middleware[R1, E1, A1In, B1In, A2Out, B2Out],
  ): Middleware[R1, E1, AIn, BIn, A2Out, B2Out] = {
    Middleware.AndThen(self, other)
  }
}

object Middleware {

  private[zhttp] object Identity extends Middleware[Any, Nothing, Nothing, Any, Any, Nothing]

  private[zhttp] final case class Transformer[R, E, A1, B1, A2, B2](f: Http[Any, Nothing, A1, B1] => Http[R, E, A2, B2])
      extends Middleware[R, E, A1, B1, A2, B2]

  private[zhttp] final case class AndThen[R, E, A1, B1, A2, B2, A3, B3](
    self: Middleware[R, E, A1, B1, A2, B2],
    other: Middleware[R, E, A2, B2, A3, B3],
  ) extends Middleware[R, E, A1, B1, A3, B3]

  private[zhttp] def execute[R, E, A1, B1, A2, B2](
    middleware: Middleware[R, E, A1, B1, A2, B2],
    http: Http[R, E, A1, B1],
  ): Http[R, E, A2, B2] =
    middleware match {
      case Identity             => http.asInstanceOf[Http[R, E, A2, B2]]
      case AndThen(self, other) => other(self(http))
      case Transformer(f: (Http[R, E, A1, B1] => Http[R, E, A2, B2]) @unchecked) => f(http)
    }

  def identity: Middleware[Any, Nothing, Nothing, Any, Any, Nothing] = Identity

  def transform[A, B]: PartiallyAppliedTransform[A, B] = PartiallyAppliedTransform(())

  def transformM[A, B]: PartiallyAppliedTransformM[A, B] = PartiallyAppliedTransformM(())

  final case class PartiallyAppliedTransform[A, B](unit: Unit) extends AnyVal {
    def apply[R, E, A1, B1](f: Http[Any, Nothing, A, B] => Http[R, E, A1, B1]): Middleware[R, E, A, B, A1, B1] =
      Transformer(f)
  }

  final case class PartiallyAppliedTransformM[A, B](unit: Unit) extends AnyVal {
    def apply[R, E, A1, B1](
      f: Http[Any, Nothing, A, B] => ZIO[R, E, Http[R, E, A1, B1]],
    ): Middleware[R, E, A, B, A1, B1] =
      transform(app => Http.fromEffect(f(app)).flatten)
  }
}
