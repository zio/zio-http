package zhttp.http

import zhttp.http.Http.{Chain, Combine, FoldM, FromEffectFunction}
import zio._

import scala.annotation.unused

/**
 * A functional domain to model Http apps using ZIO and that can work over any kind of request and response types.
 */
sealed trait Http[-R, +E, -A, +B] { self =>

  /**
   * Runs self but if it fails, runs other, ignoring the result from self.
   */
  def <>[R1 <: R, E1, A1 <: A, B1 >: B](other: Http[R1, E1, A1, B1]): Http[R1, E1, A1, B1] =
    self.foldM(_ => other, Http.succeed)

  /**
   * Combines two Http into one
   */
  def +++[R1 <: R, E1 >: E, A1 <: A, B1 >: B](other: Http[R1, E1, A1, B1]): Http[R1, E1, A1, B1] =
    Http.combine(self, other)

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
  def collect[R1 <: R, E1 >: E, A1 <: A, B1 >: B, C](pf: PartialFunction[B1, C]): Http[R1, E1, A1, C] =
    self >>> Http.collect(pf)

  /**
   * Collects some of the results of the http and effectfully converts it to another type.
   */
  def collectM[R1 <: R, E1 >: E, A1 <: A, B1 >: B, C](pf: PartialFunction[B1, ZIO[R1, E1, C]]): Http[R1, E1, A1, C] =
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
  def flatten[R1 <: R, E1 >: E, A1 <: A, B1](implicit
    ev: B <:< Http[R1, E1, A1, B1],
  ): Http[R1, E1, A1, B1] = {
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
   * Evaluates the app and returns an HttpResult that can be resolved further
   */
  def evaluate(a: A): HttpResult[R, E, B] = Http.evaluate(self: Http[R, E, A, B], a)

  /**
   * Provide the entire set of requirements to an Http instance
   */
  def provide(r: R)(implicit ev: NeedsEnv[R]): Http[Any, E, A, B] =
    provideSome(_ => r)

  /**
   * Provide a partial requirement to an Http instance
   */
  def provideSome[R0](f: R0 => R)(implicit ev: NeedsEnv[R]): Http[R0, E, A, B] =
    self match {
      case FromEffectFunction(g) => FromEffectFunction((a: A) => g(a).provideSome(f))
      case Combine(a, b)         => Combine(a.provideSome(f), b.provideSome(f))
      case Chain(a, b)           => Chain(a.provideSome(f), b.provideSome(f))
      case FoldM(a, ee, bb)      =>
        FoldM(a.provideSome(f), ee(_: Any).provideSome(f), bb(_: Any).provideSome(f))
      case _                     => self.asInstanceOf[Http[R0, E, A, B]]
    }

  /**
   * Provide a ZLayer to an Http instance to reduce its dependencies
   */
  def provideLayer[E1 >: E, R0, R1](
    layer: ZLayer[R0, E1, R1],
  )(implicit ev1: R1 <:< R, ev2: NeedsEnv[R]): Http[R0, E1, A, B] =
    self match {
      case FromEffectFunction(f) => FromEffectFunction((a: A) => f(a).provideLayer(layer))
      case Combine(a, b)         => Combine(a.provideLayer(layer), b.provideLayer(layer))
      case Chain(a, b)           => Chain(a.provideLayer(layer), b.provideLayer(layer))
      case FoldM(a, ee, bb)      =>
        FoldM(a.provideLayer(layer), ee(_: Any).provideLayer(layer), bb(_: Any).provideLayer(layer))
      case _                     => self.asInstanceOf[Http[R0, E1, A, B]]
    }

  /**
   * Provide a custom layer (which represents everything not in ZEnv) to an Http instance
   */
  def provideCustomLayer[E1 >: E, R1 <: Has[_]](
    layer: ZLayer[ZEnv, E1, R1],
  )(implicit ev: ZEnv with R1 <:< R, tagged: Tag[R1]): Http[ZEnv, E1, A, B] =
    provideSomeLayer[ZEnv, E1, R1](layer)

  /**
   * Provide a ZLayer to an Http instance
   */
  def provideSomeLayer[R0 <: Has[_], E1 >: E, R1 <: Has[_]](
    layer: ZLayer[R0, E1, R1],
  )(implicit ev1: R0 with R1 <:< R, ev2: NeedsEnv[R], tagged: Tag[R1]): Http[R0, E1, A, B] =
    self match {
      case FromEffectFunction(f) => FromEffectFunction((a: A) => f(a).provideSomeLayer(layer))
      case Combine(a, b)         => Combine(a.provideSomeLayer(layer), b.provideSomeLayer(layer))
      case Chain(a, b)           => Chain(a.provideSomeLayer(layer), b.provideSomeLayer(layer))
      case FoldM(a, ee, bb)      =>
        FoldM(a.provideSomeLayer(layer), ee(_: Any).provideSomeLayer(layer), bb(_: Any).provideSomeLayer(layer))
      case _                     => self.asInstanceOf[Http[R0, E1, A, B]]
    }
}

object Http {
  private case object Empty                                                                extends Http[Any, Nothing, Any, Nothing]
  private case object Identity                                                             extends Http[Any, Nothing, Any, Nothing]
  private case class Succeed[B](b: B)                                                      extends Http[Any, Nothing, Any, B]
  private case class Fail[E](e: E)                                                         extends Http[Any, E, Any, Nothing]
  private case class FromEffectFunction[R, E, A, B](f: A => ZIO[R, E, B])                  extends Http[R, E, A, B]
  private case class Collect[R, E, A, B](ab: PartialFunction[A, B])                        extends Http[R, E, A, B]
  private case class Chain[R, E, A, B, C](self: Http[R, E, A, B], other: Http[R, E, B, C]) extends Http[R, E, A, C]
  private case class Combine[R, E, A, B](self: Http[R, E, A, B], other: Http[R, E, A, B])  extends Http[R, E, A, B]
  private case class FoldM[R, E, EE, A, B, BB](
    self: Http[R, E, A, B],
    ee: E => Http[R, EE, A, BB],
    bb: B => Http[R, EE, A, BB],
  )                                                                                        extends Http[R, EE, A, BB]

  // Ctor Help
  final case class MakeCollectM[A](unit: Unit) extends AnyVal {
    def apply[R, E, B](pf: PartialFunction[A, ZIO[R, E, B]]): Http[R, E, A, B] =
      Http.collect[A]({ case a if pf.isDefinedAt(a) => Http.fromEffect(pf(a)) }).flatten
  }

  final case class MakeCollect[A](unit: Unit) extends AnyVal {
    def apply[B](pf: PartialFunction[A, B]): Http[Any, Nothing, A, B] = Collect(pf)
  }

  final case class MakeFromEffectFunction[A](unit: Unit) extends AnyVal {
    def apply[R, E, B](f: A => ZIO[R, E, B]): Http[R, E, A, B] = Http.FromEffectFunction(f)
  }

  final case class MakeRoute[A](unit: Unit) extends AnyVal {
    def apply[R, E, B](pf: PartialFunction[A, Http[R, E, A, B]]) =
      Http.collect[A]({ case r if pf.isDefinedAt(r) => pf(r) }).flatten
  }

  def evaluate[R, E, A, B](http: Http[R, E, A, B], a: A): HttpResult[R, E, B] =
    http match {
      case Empty                 => HttpResult.empty
      case Identity              => HttpResult.succeed(a.asInstanceOf[B])
      case Succeed(b)            => HttpResult.succeed(b)
      case Fail(e)               => HttpResult.fail(e)
      case FromEffectFunction(f) => HttpResult.effect(f(a))
      case Collect(pf)           => if (pf.isDefinedAt(a)) HttpResult.succeed(pf(a)) else HttpResult.empty
      case Chain(self, other)    => HttpResult.suspend(self.evaluate(a) >>= (other.evaluate(_)))
      case Combine(self, other)  => HttpResult.suspend(self.evaluate(a).defaultWith(other.evaluate(a)))
      case FoldM(self, ee, bb)   =>
        HttpResult.suspend(self.evaluate(a).foldM(ee(_).evaluate(a), bb(_).evaluate(a), HttpResult.empty))
    }

  /**
   * Creates an Http that always returns the same response and never fails.
   */
  def succeed[B](b: B): Http[Any, Nothing, Any, B] = Http.Succeed(b)

  /**
   * Creates an Http app from a function that returns a ZIO
   */
  def fromEffectFunction[A]: Http.MakeFromEffectFunction[A] = Http.MakeFromEffectFunction(())

  /**
   * Converts a ZIO to an Http type
   */
  def fromEffect[R, E, B](effect: ZIO[R, E, B]): Http[R, E, Any, B] = Http.fromEffectFunction(_ => effect)

  /**
   * Creates an Http that always fails
   */
  def fail[E](e: E): Http[Any, E, Any, Nothing] = Http.Fail(e)

  /**
   * Creates a pass thru Http instances
   */
  def identity[A]: Http[Any, Nothing, A, A] = Http.Identity

  /**
   * Creates an HTTP app which accepts a request and produces response.
   */
  def collect[A]: Http.MakeCollect[A] = Http.MakeCollect(())

  /**
   * Creates an HTTP app which accepts a request and produces response effectfully.
   */
  def collectM[A]: Http.MakeCollectM[A] = Http.MakeCollectM(())

  /**
   * Creates an HTTP app which for any request produces a response.
   */
  def succeedM[R, E, B](zio: ZIO[R, E, B]): Http[R, E, Any, B] = Http.fromEffectFunction(_ => zio)

  /**
   * Flattens an Http app of an Http app
   */
  def flatten[R, E, A, B](http: Http[R, E, A, Http[R, E, A, B]]): Http[R, E, A, B] =
    http.flatten

  /**
   * Flattens an Http app of an that returns an effectful response
   */
  def flattenM[R, E, A, B](http: Http[R, E, A, ZIO[R, E, B]]): Http[R, E, A, B] =
    http.flatMap(Http.fromEffect)

  /**
   * Combines two Http values into one
   */
  def combine[R, E, A, B](self: Http[R, E, A, B], other: Http[R, E, A, B]): Http[R, E, A, B] = Combine(self, other)

  /**
   * Creates an empty Http value
   */
  def empty: Http[Any, Nothing, Any, Nothing] = Http.Empty

  /**
   * Creates a Http from a pure function
   */
  def fromFunction[A]: MkTotal[A] = new MkTotal[A](())

  /**
   * Creates an Http that delegates to other Https.
   */
  def route[A]: Http.MakeRoute[A] = Http.MakeRoute(())

  final class MkTotal[A](val unit: Unit) extends AnyVal {
    def apply[B](f: A => B): Http[Any, Nothing, A, B] = Http.identity[A].map(f)
  }
}
