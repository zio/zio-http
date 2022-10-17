package zio.http.api

import zio.http._
import zio.{Exit, ZIO}

/**
 * A `Middleware` represents the implementation of a `MiddlewareSpec`,
 * intercepting parts of the request, and appending to the response.
 */
sealed trait Middleware[-R, +E, I, O] { self =>
  def ++[R1 <: R, E1 >: E, I2, O2](that: Middleware[R1, E1, I2, O2])(implicit
    inCombiner: Combiner[I, I2],
    outCombiner: Combiner[O, O2],
  ): Middleware[R1, E1, inCombiner.Out, outCombiner.Out] =
    Middleware.Concat[R1, E1, I, O, I2, O2, inCombiner.Out, outCombiner.Out](self, that, inCombiner, outCombiner)

}

object Middleware {
  def bypass(
    middlewareSpec: MiddlewareSpec[Request, Response],
    f: Request => Response,
  ): Middleware[Any, Nothing, Any, Nothing] = Bypass(middlewareSpec, f)

  def bypassZIO[R, E](
    middlewareSpec: MiddlewareSpec[Request, Response],
    f: Request => ZIO[R, E, Response],
  ): Middleware[R, E, Any, Nothing] = BypassZIO(middlewareSpec: MiddlewareSpec[Request, Response], f)

  def fromFunction[A, B](middlewareSpec: MiddlewareSpec[A, B], f: A => B): Middleware[Any, Nothing, A, B] =
    Handler(middlewareSpec, f)

  def fromFunctionZIO[R, E, A, B](middlewareSpec: MiddlewareSpec[A, B], f: A => ZIO[R, E, B]): Middleware[R, E, A, B] =
    HandlerZIO(middlewareSpec, f)

  def ifThenElse[R, E, I, O](
    middlewareSpec: MiddlewareSpec[I, O],
    pred: I => Boolean,
  )(ifTrue: Middleware[R, E, I, O], ifFalse: Middleware[R, E, I, O]): Middleware[R, E, I, O] =
    IfThenElse(middlewareSpec, pred, ifTrue, ifFalse)

  def intercept[S, I, O](middlewareSpec: MiddlewareSpec[I, O], incoming: I => S)(
    outgoing: (Exit[Any, Any], S) => O,
  ): Middleware[Any, Nothing, I, O] =
    Intercept(middlewareSpec, incoming, outgoing)

  def interceptZIO[R, E, S, I, O](middlewareSpec: MiddlewareSpec[I, O], incoming: I => ZIO[R, E, S])(
    outgoing: (Exit[Any, Any], S) => ZIO[R, E, O],
  ): Middleware[R, E, I, O] =
    InterceptZIO(middlewareSpec, incoming, outgoing)

  val none: Middleware[Any, Nothing, Unit, Unit] = Handler(MiddlewareSpec.none, _ => ())

  private[api] final case class HandlerZIO[-R, +E, I, O](
    middlewareSpec: MiddlewareSpec[I, O],
    handler: I => ZIO[R, E, O],
  ) extends Middleware[R, E, I, O]

  private[api] final case class Concat[-R, +E, I1, O1, I2, O2, I3, O3](
    left: Middleware[R, E, I1, O1],
    right: Middleware[R, E, I2, O2],
    inCombiner: Combiner.WithOut[I1, I2, I3],
    outCombiner: Combiner.WithOut[O1, O2, O3],
  ) extends Middleware[R, E, I3, O3]

  private[api] final case class Handler[I, O](middlewareSpec: MiddlewareSpec[I, O], handler: I => O)
      extends Middleware[Any, Nothing, I, O]

  private[api] final case class BypassZIO[R, E](
    middlewareSpec: MiddlewareSpec[Request, Response],
    execute: Request => ZIO[R, E, Response],
  ) extends Middleware[R, E, Any, Nothing]

  private[api] final case class Bypass(middlewareSpec: MiddlewareSpec[Request, Response], execute: Request => Response)
      extends Middleware[Any, Nothing, Any, Nothing]

  private[api] final case class IfThenElse[R, E, I, O](
    middlewareSpec: MiddlewareSpec[I, O],
    predicate: I => Boolean,
    ifTrue: Middleware[R, E, I, O],
    ifFalse: Middleware[R, E, I, O],
  ) extends Middleware[R, E, I, O]

  private[api] final case class Intercept[S, I, O](
    middlewareSpec: MiddlewareSpec[I, O],
    incoming: I => S,
    outgoing: (Exit[Any, Any], S) => O,
  ) extends Middleware[Any, Nothing, I, O]

  private[api] final case class InterceptZIO[R, E, S, I, O](
    middlewareSpec: MiddlewareSpec[I, O],
    incoming: I => ZIO[R, E, S],
    outgoing: (Exit[Any, Any], S) => ZIO[R, E, O],
  ) extends Middleware[R, E, I, O]
}
