package zio.http.api

import zio.http._
import zio.{Exit, ZIO}

/**
 * A `Middleware` represents the implementation of a `MiddlewareSpec`,
 * intercepting parts of the request, and appending to the response.
 */
sealed trait Middleware[-R, +E, -I, +O] { self => }
object Middleware                       {
  def bypass(f: Request => Response): Middleware[Any, Nothing, Any, Nothing] = Bypass(f)

  def bypassZIO[R, E](f: Request => ZIO[R, E, Response]): Middleware[R, E, Any, Nothing] = BypassZIO(f)

  def fromFunction[A, B](f: A => B): Middleware[Any, Nothing, A, B] = Handler(f)

  def fromFunctionZIO[R, E, A, B](f: A => ZIO[R, E, B]): Middleware[R, E, A, B] = HandlerZIO(f)

  def ifThenElse[R, E, I, O](
    pred: I => Boolean,
  )(ifTrue: Middleware[R, E, I, O], ifFalse: Middleware[R, E, I, O]): Middleware[R, E, I, O] =
    IfThenElse(pred, ifTrue, ifFalse)

  def intercept[S, I, O](incoming: I => S)(outgoing: (Exit[Any, Any], S) => O): Middleware[Any, Nothing, I, O] =
    Intercept(incoming, outgoing)

  def interceptZIO[R, E, S, I, O](incoming: I => ZIO[R, E, S])(
    outgoing: (Exit[Any, Any], S) => ZIO[R, E, O],
  ): Middleware[R, E, I, O] =
    InterceptZIO(incoming, outgoing)

  val none: Middleware[Any, Nothing, Unit, Unit] = Handler(_ => ())

  private[api] final case class HandlerZIO[-R, +E, I, O](handler: I => ZIO[R, E, O]) extends Middleware[R, E, I, O]

  private[api] final case class Handler[I, O](handler: I => O) extends Middleware[Any, Nothing, I, O]

  private[api] final case class BypassZIO[R, E](execute: Request => ZIO[R, E, Response])
      extends Middleware[R, E, Any, Nothing]

  private[api] final case class Bypass(execute: Request => Response) extends Middleware[Any, Nothing, Any, Nothing]

  private[api] final case class IfThenElse[R, E, I, O](
    predicate: I => Boolean,
    ifTrue: Middleware[R, E, I, O],
    ifFalse: Middleware[R, E, I, O],
  ) extends Middleware[R, E, I, O]

  private[api] final case class Intercept[S, I, O](incoming: I => S, outgoing: (Exit[Any, Any], S) => O)
      extends Middleware[Any, Nothing, I, O]

  private[api] final case class InterceptZIO[R, E, S, I, O](
    incoming: I => ZIO[R, E, S],
    outgoing: (Exit[Any, Any], S) => ZIO[R, E, O],
  ) extends Middleware[R, E, I, O]
}
