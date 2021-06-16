package zhttp.http

import zio.{CanFail, ZIO}

import scala.annotation.unused

/**
 * A functional domain to model Http apps using ZIO and that can work over any kind of request and response types.
 */
sealed trait Http[-R, +E, -A, +B] { self =>

  import Http._

  /**
   * Runs self but if it fails, runs other, ignoring the result from self.
   */
  def <>[R1 <: R, E1, A1 <: A, B1 >: B](other: Http[R1, E1, A1, B1]): Http[R1, E1, A1, B1] =
    self orElse other

  /**
   * Named alias for `<>`
   */
  def orElse[R1 <: R, E1, A1 <: A, B1 >: B](other: Http[R1, E1, A1, B1]): Http[R1, E1, A1, B1] =
    self.catchAll(_ => other)

  /**
   * Combines two Http into one.
   */
  def +++[R1 <: R, E1 >: E, A1 <: A, B1 >: B](other: Http[R1, E1, A1, B1]): Http[R1, E1, A1, B1] =
    self defaultWith other

  /**
   * Named alias for `+++`
   */
  def defaultWith[R1 <: R, E1 >: E, A1 <: A, B1 >: B](other: Http[R1, E1, A1, B1]): Http[R1, E1, A1, B1] =
    self.foldM(Http.fail, Http.succeed, other)

  /**
   * Pipes the output of one app into the other
   */
  def >>>[R1 <: R, E1 >: E, B1 >: B, C](other: Http[R1, E1, B1, C]): Http[R1, E1, A, C] =
    self andThen other

  /**
   * Named alias for `>>>`
   */
  def andThen[R1 <: R, E1 >: E, B1 >: B, C](other: Http[R1, E1, B1, C]): Http[R1, E1, A, C] =
    Http.Chain(self, other)

  /**
   * Composes one Http app with another.
   */
  def <<<[R1 <: R, E1 >: E, A1 <: A, X](other: Http[R1, E1, X, A1]): Http[R1, E1, X, B] =
    self compose other

  /**
   * Named alias for `<<<`
   */
  def compose[R1 <: R, E1 >: E, A1 <: A, C1](other: Http[R1, E1, C1, A1]): Http[R1, E1, C1, B] =
    other andThen self

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
    self.catchAll(e => Http.succeed(s.silent(e)))

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
  def contramap[X](xa: X => A): Http[R, E, X, B] = Http.identity[X].map(xa) >>> self

  /**
   * Transforms the output of the http effectfully
   */
  def mapM[R1 <: R, E1 >: E, C](bFc: B => ZIO[R1, E1, C]): Http[R1, E1, A, C] =
    self >>> Http.fromEffectFunction(bFc)

  /**
   * Transforms the input of the http before giving it effectfully
   */
  def contramapM[R1 <: R, E1 >: E, X](xa: X => ZIO[R1, E1, A]): Http[R1, E1, X, B] =
    Http.fromEffectFunction[X](xa) >>> self

  /**
   * Flattens an Http app of an Http app
   */
  def flatten[R1 <: R, E1 >: E, A1 <: A, B1](implicit
    ev: B <:< Http[R1, E1, A1, B1],
  ): Http[R1, E1, A1, B1] = {
    self.flatMap(scala.Predef.identity(_))
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
    self.foldM(Http.fail, f, Http.empty)
  }

  /**
   * Catches all the exceptions that the http app can fail with
   */
  def catchAll[R1 <: R, E1, A1 <: A, B1 >: B](f: E => Http[R1, E1, A1, B1])(implicit
    @unused ev: CanFail[E],
  ): Http[R1, E1, A1, B1] =
    self.foldM(f, Http.succeed, Http.empty)

  /**
   * Folds over the http app by taking in two functions one for success and one for failure respectively.
   */
  def foldM[R1 <: R, A1 <: A, E1, B1](
    ee: E => Http[R1, E1, A1, B1],
    bb: B => Http[R1, E1, A1, B1],
    dd: Http[R1, E1, A1, B1],
  ): Http[R1, E1, A1, B1] = Http.FoldM(self, ee, bb, dd)

  /**
   * Unwraps an Http that returns a ZIO of Http
   */
  def unwrap[R1 <: R, E1 >: E, C](implicit ev: B <:< ZIO[R1, E1, C]): Http[R1, E1, A, C] =
    self.flatMap(Http.fromEffect(_))

  /**
   * Wraps an `Http` app in a surrounding effect that controls the execution of the underlying `Http`.
   */
  def wrap[R1 <: R, A1 <: A, E1 >: E, C](
    next: (A1, ZIO[R, Option[E], B]) => ZIO[R1, E1, C],
  ): Http[R1, E1, A1, C] =
    Http.Wrap(self, next)

  /**
   * Evaluates the app and returns an HttpResult that can be resolved further
   */
  final private[zhttp] def execute(a: A): HttpResult[R, E, B] = {
    self match {
      case Empty                   => HttpResult.empty
      case Identity                => HttpResult.succeed(a.asInstanceOf[B])
      case Succeed(b)              => HttpResult.succeed(b)
      case Fail(e)                 => HttpResult.fail(e)
      case FromEffectFunction(f)   => HttpResult.effect(f(a))
      case Collect(pf)             => if (pf.isDefinedAt(a)) HttpResult.succeed(pf(a)) else HttpResult.empty
      case Chain(self, other)      => HttpResult.suspend(self.execute(a) >>= (other.execute(_)))
      case FoldM(self, ee, bb, dd) =>
        HttpResult.suspend {
          self.execute(a).foldM(ee(_).execute(a), bb(_).execute(a), dd.execute(a))
        }
      case Wrap(self, wrapper)     => {
        val eff = self.execute(a).evaluate.asEffect
        HttpResult.suspend(HttpResult.effect(wrapper(a, eff)))
      }
    }
  }
}

object Http {
  private case object Empty                                                     extends Http[Any, Nothing, Any, Nothing]
  private case object Identity                                                  extends Http[Any, Nothing, Any, Nothing]
  private final case class Succeed[B](b: B)                                     extends Http[Any, Nothing, Any, B]
  private final case class Fail[E](e: E)                                        extends Http[Any, E, Any, Nothing]
  private final case class FromEffectFunction[R, E, A, B](f: A => ZIO[R, E, B]) extends Http[R, E, A, B]
  private final case class Collect[R, E, A, B](ab: PartialFunction[A, B])       extends Http[R, E, A, B]
  private final case class Chain[R, E, A, B, C](self: Http[R, E, A, B], other: Http[R, E, B, C])
      extends Http[R, E, A, C]
  private final case class Wrap[R, R1 <: R, E, E1 >: E, A, B, C](
    self: Http[R, E, A, B],
    wrapper: (A, ZIO[R, Option[E], B]) => ZIO[R1, E1, C],
  )                                                                             extends Http[R1, E1, A, C]
  private final case class FoldM[R, E, EE, A, B, BB](
    self: Http[R, E, A, B],
    ee: E => Http[R, EE, A, BB],
    bb: B => Http[R, EE, A, BB],
    dd: Http[R, EE, A, BB],
  )                                                                             extends Http[R, EE, A, BB]

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
    def apply[R, E, B](pf: PartialFunction[A, Http[R, E, A, B]]): Http[R, E, A, B] =
      Http.collect[A]({ case r if pf.isDefinedAt(r) => pf(r) }).flatten
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
   * Combines multiple Http apps into one
   */
  def combine[R, E, A, B](i: Iterable[Http[R, E, A, B]]): Http[R, E, A, B] =
    i.reduce(_.defaultWith(_))

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
