package zhttp.http

import zio.{CanFail, ZIO}

import scala.annotation.unused

/**
 * A functional domain to model Http apps using ZIO and that can work over any kind of request and response types.
 */
sealed trait Http[-R, +E, -A, +B] { self =>

  /**
   * Combines two HTTP apps.
   */
  def <>[R1 <: R, E1 >: E, A1 <: A, B1 >: B](other: Http[R1, E1, A1, B1])(implicit
    ev: CanConcatenate[E1],
  ): Http[R1, E1, A1, B1] = self.foldM(e => if (ev.is(e)) other else Http.fail(e), a => Http.succeed(a))

  /**
   * Pipes the output of one app into the other
   */
  def >>>[R1 <: R, E1 >: E, A1 <: A, B1 >: B, C1](other: Http[R1, E1, B1, C1]): Http[R1, E1, A1, C1] =
    Http.Chain(self, other)

  /**
   * Alias for flatmap
   */
  def >>=[R1 <: R, E1 >: E, A1 <: A, C1](f: B => Http[R1, E1, A1, C1]): Http[R1, E1, A1, C1] =
    self.flatMap(f)

  /**
   * Alias for zipRight
   */
  def *>[R1 <: R, E1 >: E, A1 <: A, C1](other: Http[R1, E1, A1, C1]): Http[R1, E1, A1, C1] =
    self.zipRight(other)

  /**
   * Combines the two apps and returns the result of the one on the right
   */
  def zipRight[R1 <: R, E1 >: E, A1 <: A, C1](other: Http[R1, E1, A1, C1]): Http[R1, E1, A1, C1] =
    self.flatMap(_ => other)

  /**
   * Converts a failing Http into a non-failing one by handling the failure and converting it to a result if possible.
   */
  def silent[E1 >: E, B1 >: B](implicit s: CanBeSilenced[E1, B1]): Http[R, Nothing, A, B1] =
    self.foldM(e => Http.succeed(s.silent(e)), Http.succeed)

  /**
   * Collects some of the results of the http and converts it to another type.
   */
  def collect[R1 <: R, E1 >: E, A1 <: A, B1 >: B, C](
    pf: PartialFunction[B1, C],
  )(implicit error: CanSupportPartial[B1, E1]): Http[R1, E1, A1, C] =
    self >>> Http.collect(pf)

  /**
   * Collects some of the results of the http and effectfully converts it to another type.
   */
  def collectM[R1 <: R, E1 >: E, A1 <: A, B1 >: B, C](
    pf: PartialFunction[B1, ZIO[R1, E1, C]],
  )(implicit error: CanSupportPartial[B1, E1]): Http[R1, E1, A1, C] =
    self >>> Http.collectM(pf)

  /**
   * Transforms the output of the http app
   */
  def map[C](bc: B => C): Http[R, E, A, C] = self.flatMap(b => Http.succeed(bc(b)))

  /**
   * Transforms the input of the http before giving it
   */
  def cmap[X](xa: X => A): Http[R, E, X, B] = Http.identity[X].map(xa) >>> self

  /**
   * Transforms the output of the http effectfully
   */
  def mapM[R1 <: R, E1 >: E, C](bFc: B => ZIO[R1, E1, C]): Http[R1, E1, A, C] =
    self >>> Http.fromEffectFunction(bFc)

  /**
   * Transforms the input of the http before giving it effectfully
   */
  def cmapM[R1 <: R, E1 >: E, X](xa: X => ZIO[R1, E1, A]): Http[R1, E1, X, B] =
    Http.fromEffectFunction[X](xa) >>> self

  /**
   * Flattens an Http app of an Http app
   */
  def flatten[R1 <: R, E1 >: E, A1 <: A, B1](implicit ev: B <:< Http[R1, E1, A1, B1]): Http[R1, E1, A1, B1] = {
    self.flatMap(identity(_))
  }

  /**
   * Widens the type of the output
   */
  def widen[B1](implicit ev: B <:< B1): Http[R, E, A, B1] =
    self.asInstanceOf[Http[R, E, A, B1]]

  /**
   * Makes the app resolve with a constant value
   */
  def as[C](c: C): Http[R, E, A, C] =
    self *> Http.succeed(c)

  /**
   * Creates a new Http app from another
   */
  def flatMap[R1 <: R, E1 >: E, A1 <: A, C1](f: B => Http[R1, E1, A1, C1]): Http[R1, E1, A1, C1] = {
    self.foldM(Http.fail, f)
  }

  /**
   * Catches all the exceptions that the http app can fail with
   */
  def catchAll[R1 <: R, E1, A1 <: A, B1 >: B](f: E => Http[R1, E1, A1, B1])(implicit
    @unused ev: CanFail[E],
  ): Http[R1, E1, A1, B1] =
    self.foldM(f, Http.succeed)

  /**
   * Folds over the http app by taking in two functions one for success and one for failure respectively.
   */
  def foldM[R1 <: R, A1 <: A, E1, B1](
    ee: E => Http[R1, E1, A1, B1],
    bb: B => Http[R1, E1, A1, B1],
  ): Http[R1, E1, A1, B1] = Http.FoldM(self, ee, bb)

  /**
   * Evaluates the Http app in an executionally optimized way.
   */
  def eval(a: => A): HttpResult.Out[R, E, B] = Http.evalSuspended(self, a).evaluate

  /**
   * Evalutes the app and suspends immediately.
   */
  def evalSuspended(a: => A): HttpResult[R, E, B] = Http.evalSuspended(self, a)

  /**
   * Evaluates the app as a ZIO
   */
  def evalAsEffect(a: => A): ZIO[R, E, B] = self.eval(a).asEffect

  def apply(req: => A): ZIO[R, E, B] = self.evalAsEffect(req)
}

object Http extends HttpConstructors with HttpExecutors {
  final case object Identity                                                             extends Http[Any, Nothing, Any, Nothing]
  final case class Succeed[B](b: B)                                                      extends Http[Any, Nothing, Any, B]
  final case class Fail[E](e: E)                                                         extends Http[Any, E, Any, Nothing]
  final case class FromEffectFunction[R, E, A, B](f: A => ZIO[R, E, B])                  extends Http[R, E, A, B]
  final case class Chain[R, E, A, B, C](self: Http[R, E, A, B], other: Http[R, E, B, C]) extends Http[R, E, A, C]
  final case class FoldM[R, E, EE, A, B, BB](
    self: Http[R, E, A, B],
    ee: E => Http[R, EE, A, BB],
    bb: B => Http[R, EE, A, BB],
  )                                                                                      extends Http[R, EE, A, BB]

  // Ctor Help
  final case class MakeCollectM[A](unit: Unit) extends AnyVal {
    def apply[R, E, B](
      pf: PartialFunction[A, ZIO[R, E, B]],
    )(implicit error: CanSupportPartial[A, E]): Http[R, E, A, B] =
      Http.fromEffectFunction[A](a => if (pf.isDefinedAt(a)) pf(a) else ZIO.fail(error(a)))
  }

  final case class MakeCollect[A](unit: Unit) extends AnyVal {
    def apply[R, E, B](
      pf: PartialFunction[A, B],
    )(implicit error: CanSupportPartial[A, E]): Http[R, E, A, B] = Http.identity[A] >>=
      (a => if (pf.isDefinedAt(a)) Http.succeed(pf(a)) else Http.fail(error(a)))
  }

  final case class MakeFromEffectFunction[A](unit: Unit) extends AnyVal {
    def apply[R, E, B](f: A => ZIO[R, E, B]): Http[R, E, A, B] = Http.FromEffectFunction(f)
  }

}
