package zhttp.http

import zio.{CanFail, ZIO}

import scala.annotation.unused

/**
 * A functional domain to model Http apps using ZIO and that can work over any kind of request and response types.
 */
sealed trait HttpChannel[-R, +E, -A, +B] { self =>

  /**
   * Combines two HTTP apps.
   */
  def <>[R1 <: R, E1 >: E, A1 <: A, B1 >: B](other: HttpChannel[R1, E1, A1, B1])(implicit
    ev: CanConcatenate[E1],
  ): HttpChannel[R1, E1, A1, B1] =
    self.foldM(e => if (ev.is(e)) other else HttpChannel.fail(e), a => HttpChannel.succeed(a))

  /**
   * Pipes the output of one app into the other
   */
  def >>>[R1 <: R, E1 >: E, A1 <: A, B1 >: B, C1](other: HttpChannel[R1, E1, B1, C1]): HttpChannel[R1, E1, A1, C1] =
    HttpChannel.Chain(self, other)

  /**
   * Alias for flatmap
   */
  def >>=[R1 <: R, E1 >: E, A1 <: A, C1](f: B => HttpChannel[R1, E1, A1, C1]): HttpChannel[R1, E1, A1, C1] =
    self.flatMap(f)

  /**
   * Alias for zipRight
   */
  def *>[R1 <: R, E1 >: E, A1 <: A, C1](other: HttpChannel[R1, E1, A1, C1]): HttpChannel[R1, E1, A1, C1] =
    self.zipRight(other)

  /**
   * Combines the two apps and returns the result of the one on the right
   */
  def zipRight[R1 <: R, E1 >: E, A1 <: A, C1](other: HttpChannel[R1, E1, A1, C1]): HttpChannel[R1, E1, A1, C1] =
    self.flatMap(_ => other)

  /**
   * Converts a failing Http into a non-failing one by handling the failure and converting it to a result if possible.
   */
  def silent[E1 >: E, B1 >: B](implicit s: CanBeSilenced[E1, B1]): HttpChannel[R, Nothing, A, B1] =
    self.foldM(e => HttpChannel.succeed(s.silent(e)), HttpChannel.succeed)

  /**
   * Collects some of the results of the http and converts it to another type.
   */
  def collect[R1 <: R, E1 >: E, A1 <: A, B1 >: B, C](
    pf: PartialFunction[B1, C],
  )(implicit error: CanSupportPartial[B1, E1]): HttpChannel[R1, E1, A1, C] =
    self >>> HttpChannel.collect(pf)

  /**
   * Collects some of the results of the http and effectfully converts it to another type.
   */
  def collectM[R1 <: R, E1 >: E, A1 <: A, B1 >: B, C](
    pf: PartialFunction[B1, ZIO[R1, E1, C]],
  )(implicit error: CanSupportPartial[B1, E1]): HttpChannel[R1, E1, A1, C] =
    self >>> HttpChannel.collectM(pf)

  /**
   * Transforms the output of the http app
   */
  def map[C](bc: B => C): HttpChannel[R, E, A, C] = self.flatMap(b => HttpChannel.succeed(bc(b)))

  /**
   * Transforms the input of the http before giving it
   */
  def cmap[X](xa: X => A): HttpChannel[R, E, X, B] = HttpChannel.identity[X].map(xa) >>> self

  /**
   * Transforms the output of the http effectfully
   */
  def mapM[R1 <: R, E1 >: E, C](bFc: B => ZIO[R1, E1, C]): HttpChannel[R1, E1, A, C] =
    self >>> HttpChannel.fromEffectFunction(bFc)

  /**
   * Transforms the input of the http before giving it effectfully
   */
  def cmapM[R1 <: R, E1 >: E, X](xa: X => ZIO[R1, E1, A]): HttpChannel[R1, E1, X, B] =
    HttpChannel.fromEffectFunction[X](xa) >>> self

  /**
   * Flattens an Http app of an Http app
   */
  def flatten[R1 <: R, E1 >: E, A1 <: A, B1](implicit
    ev: B <:< HttpChannel[R1, E1, A1, B1],
  ): HttpChannel[R1, E1, A1, B1] = {
    self.flatMap(identity(_))
  }

  /**
   * Widens the type of the output
   */
  def widen[B1](implicit ev: B <:< B1): HttpChannel[R, E, A, B1] =
    self.asInstanceOf[HttpChannel[R, E, A, B1]]

  /**
   * Makes the app resolve with a constant value
   */
  def as[C](c: C): HttpChannel[R, E, A, C] =
    self *> HttpChannel.succeed(c)

  /**
   * Creates a new Http app from another
   */
  def flatMap[R1 <: R, E1 >: E, A1 <: A, C1](f: B => HttpChannel[R1, E1, A1, C1]): HttpChannel[R1, E1, A1, C1] = {
    self.foldM(HttpChannel.fail, f)
  }

  /**
   * Catches all the exceptions that the http app can fail with
   */
  def catchAll[R1 <: R, E1, A1 <: A, B1 >: B](f: E => HttpChannel[R1, E1, A1, B1])(implicit
    @unused ev: CanFail[E],
  ): HttpChannel[R1, E1, A1, B1] =
    self.foldM(f, HttpChannel.succeed)

  /**
   * Folds over the http app by taking in two functions one for success and one for failure respectively.
   */
  def foldM[R1 <: R, A1 <: A, E1, B1](
    ee: E => HttpChannel[R1, E1, A1, B1],
    bb: B => HttpChannel[R1, E1, A1, B1],
  ): HttpChannel[R1, E1, A1, B1] = HttpChannel.FoldM(self, ee, bb)

  /**
   * Evaluates the Http app in an executionally optimized way.
   */
  def eval(a: => A): HttpResult.Out[R, E, B] = HttpChannel.evalSuspended(self, a).evaluate

  /**
   * Evalutes the app and suspends immediately.
   */
  def evalSuspended(a: => A): HttpResult[R, E, B] = HttpChannel.evalSuspended(self, a)

  /**
   * Evaluates the app as a ZIO
   */
  def evalAsEffect(a: => A): ZIO[R, E, B] = self.eval(a).asEffect

  def apply(req: => A): ZIO[R, E, B] = self.evalAsEffect(req)
}

object HttpChannel extends HttpConstructors with HttpExecutors {
  case object Identity                                            extends HttpChannel[Any, Nothing, Any, Nothing]
  case class Succeed[B](b: B)                                     extends HttpChannel[Any, Nothing, Any, B]
  case class Fail[E](e: E)                                        extends HttpChannel[Any, E, Any, Nothing]
  case class FromEffectFunction[R, E, A, B](f: A => ZIO[R, E, B]) extends HttpChannel[R, E, A, B]
  case class Chain[R, E, A, B, C](self: HttpChannel[R, E, A, B], other: HttpChannel[R, E, B, C])
      extends HttpChannel[R, E, A, C]
  case class FoldM[R, E, EE, A, B, BB](
    self: HttpChannel[R, E, A, B],
    ee: E => HttpChannel[R, EE, A, BB],
    bb: B => HttpChannel[R, EE, A, BB],
  )                                                               extends HttpChannel[R, EE, A, BB]

  // Ctor Help
  final case class MakeCollectM[A](unit: Unit) extends AnyVal {
    def apply[R, E, B](
      pf: PartialFunction[A, ZIO[R, E, B]],
    )(implicit error: CanSupportPartial[A, E]): HttpChannel[R, E, A, B] =
      HttpChannel.fromEffectFunction[A](a => if (pf.isDefinedAt(a)) pf(a) else ZIO.fail(error(a)))
  }

  final case class MakeCollect[A](unit: Unit) extends AnyVal {
    def apply[R, E, B](
      pf: PartialFunction[A, B],
    )(implicit error: CanSupportPartial[A, E]): HttpChannel[R, E, A, B] = HttpChannel.identity[A] >>=
      (a => if (pf.isDefinedAt(a)) HttpChannel.succeed(pf(a)) else HttpChannel.fail(error(a)))
  }

  final case class MakeFromEffectFunction[A](unit: Unit) extends AnyVal {
    def apply[R, E, B](f: A => ZIO[R, E, B]): HttpChannel[R, E, A, B] = HttpChannel.FromEffectFunction(f)
  }

}
