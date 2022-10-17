package zio.http.api

import zio.http._
import zio.http.middleware.HttpMiddleware
import zio.http.model.Cookie
import zio.{Exit, ZIO}

/**
 * A `Middleware` represents the implementation of a `MiddlewareSpec`,
 * intercepting parts of the request, and appending to the response.
 */
sealed trait Middleware[-R, +E, I, O] extends Middleware.Aspect[R, E] { self =>
  def ++[R1 <: R, E1 >: E, I2, O2](that: Middleware[R1, E1, I2, O2])(implicit
    inCombiner: Combiner[I, I2],
    outCombiner: Combiner[O, O2],
  ): Middleware[R1, E1, inCombiner.Out, outCombiner.Out] =
    Middleware.Concat[R1, E1, I, O, I2, O2, inCombiner.Out, outCombiner.Out](self, that, inCombiner, outCombiner)

  def spec: MiddlewareSpec[I, O] =
    self match {
      case Middleware.HandlerZIO(middlewareSpec, _)                => middlewareSpec
      case Middleware.Concat(left, right, inCombiner, outCombiner) => left.spec.++(right.spec)(inCombiner, outCombiner)
      case Middleware.Handler(middlewareSpec, _)                   => middlewareSpec
      case Middleware.PeekRequest(middleware)                      => middleware.spec
    }

}

object Middleware {

  trait Aspect[-R, +E, -O] {
    def apply[R1 <: R, E1 >: E](http: HttpApp[R1, E1], out: O): HttpApp[R1, E1]
  }

  object Aspect {
    def identity = new Aspect[Any, Nothing, Any] {
      override def apply[R1 <: Any, E1 >: Nothing](http: HttpApp[R1, E1], out: Any): HttpApp[R1, E1] =
        http
    }
  }

  /**
   * Sets cookie in response headers
   */
  def addCookie(cookie: Cookie[Response]): Middleware[Any, Nothing, Unit, Cookie[Response]] =
    fromFunction(new Aspect[Any, Nothing, Cookie[Response]] {
      override def apply[R1 <: Any, E1 >: Nothing](http: HttpApp[R1, E1], out: Cookie[Response]): HttpApp[R1, E1] =
        http @@ zio.http.Middleware.addCookie(out)
    }, MiddlewareSpec.addCookie, _ => cookie)

  def fromFunction[A, B](
    aspect: Aspect[Any, Nothing],
    middlewareSpec: MiddlewareSpec[A, B],
    f: A => B,
  ): Middleware[Any, Nothing, A, B] =
    Handler(middlewareSpec, f)

  def fromFunctionZIO[R, E, A, B](
    aspect: Aspect[R, E],
    middlewareSpec: MiddlewareSpec[A, B],
    f: A => ZIO[R, E, B],
  ): Middleware[R, E, A, B] =
    HandlerZIO(middlewareSpec, f)

  val none: Middleware[Any, Nothing, Unit, Unit] =
    fromFunction(Aspect.identity, MiddlewareSpec.none, _ => ())

  private[api] final case class HandlerZIO[-R, +E, I, O](
    middlewareSpec: MiddlewareSpec[I, O],
    handler: I => ZIO[R, E, O],
  ) extends Middleware[R, E, I, O]

  private[api] final case class PeekRequest[-R, +E, I, O](
    middleware: Middleware[R, E, I, O],
  ) extends Middleware[R, E, (I, Request), O]

  private[api] final case class Concat[-R, +E, I1, O1, I2, O2, I3, O3](
    left: Middleware[R, E, I1, O1],
    right: Middleware[R, E, I2, O2],
    inCombiner: Combiner.WithOut[I1, I2, I3],
    outCombiner: Combiner.WithOut[O1, O2, O3],
  ) extends Middleware[R, E, I3, O3]

  private[api] final case class Handler[I, O](middlewareSpec: MiddlewareSpec[I, O], handler: I => O)
      extends Middleware[Any, Nothing, I, O]
}
