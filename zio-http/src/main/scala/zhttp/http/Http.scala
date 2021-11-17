package zhttp.http

import io.netty.channel.ChannelHandler
import zhttp.http.IsHttpApp.{IsRequest, IsResponse}
import zhttp.http.middleware.{HttpMiddleware, Patch}
import zhttp.service.server.ServerTimeGenerator
import zhttp.service.{Handler, HttpRuntime, Server}
import zio._
import zio.clock.Clock
import zio.duration.Duration

import scala.annotation.unused

/**
 * A functional domain to model Http apps using ZIO and that can work over any kind of request and response types.
 */
sealed trait Http[-R, +E, -A, +B] extends (A => ZIO[R, Option[E], B]) { self =>

  import Http._

  /**
   * Attaches the provided middleware to the HttpApp
   */
  final def @@[R1 <: R, E1 >: E](mid: HttpMiddleware[R1, E1])(implicit ev: IsHttpApp[R1, E1, A, B]): HttpApp[R1, E1] =
    self.middleware(mid)

  /**
   * Alias for flatmap
   */
  final def >>=[R1 <: R, E1 >: E, A1 <: A, C1](f: B => Http[R1, E1, A1, C1]): Http[R1, E1, A1, C1] =
    self.flatMap(f)

  /**
   * Pipes the output of one app into the other
   */
  final def >>>[R1 <: R, E1 >: E, B1 >: B, C](other: Http[R1, E1, B1, C]): Http[R1, E1, A, C] =
    self andThen other

  /**
   * Runs self but if it fails, runs other, ignoring the result from self.
   */
  final def <>[R1 <: R, E1, A1 <: A, B1 >: B](other: Http[R1, E1, A1, B1]): Http[R1, E1, A1, B1] =
    self orElse other

  /**
   * Composes one Http app with another.
   */
  final def <<<[R1 <: R, E1 >: E, A1 <: A, X](other: Http[R1, E1, X, A1]): Http[R1, E1, X, B] =
    self compose other

  /**
   * Combines two Http into one.
   */
  final def +++[R1 <: R, E1 >: E, A1 <: A, B1 >: B](other: Http[R1, E1, A1, B1]): Http[R1, E1, A1, B1] =
    self defaultWith other

  /**
   * Alias for zipRight
   */
  final def *>[R1 <: R, E1 >: E, A1 <: A, C1](other: Http[R1, E1, A1, C1]): Http[R1, E1, A1, C1] =
    self.zipRight(other)

  /**
   * Adds the provided headers to the response of the app
   */
  final def addHeader(header: Header)(implicit evB: IsResponse[R, E, B]): Http[R, E, A, Response[R, E]] =
    self.patch(Patch.addHeader(header))

  /**
   * Adds the provided header to the response of the app
   */
  final def addHeader(name: String, value: String)(implicit evB: IsResponse[R, E, B]): Http[R, E, A, Response[R, E]] =
    self.patch(Patch.addHeader(name, value))

  /**
   * Adds the provided headers to the response of the app
   */
  final def addHeaders(headers: List[Header])(implicit evB: IsResponse[R, E, B]): Http[R, E, A, Response[R, E]] =
    self.patch(Patch.addHeaders(headers))

  /**
   * Named alias for `>>>`
   */
  final def andThen[R1 <: R, E1 >: E, B1 >: B, C](other: Http[R1, E1, B1, C]): Http[R1, E1, A, C] =
    Http.Chain(self, other)

  /**
   * Consumes the input and executes the Http.
   */
  final def apply(a: A): ZIO[R, Option[E], B] = execute(a).evaluate.asEffect

  /**
   * Makes the app resolve with a constant value
   */
  final def as[C](c: C): Http[R, E, A, C] =
    self *> Http.succeed(c)

  final def asApp[R1 <: R, E1 >: E, A1 <: A, B1 >: B](implicit ev: IsHttpApp[R1, E1, A1, B1]): HttpApp[R, E] =
    self.asInstanceOf[HttpApp[R, E]]

  /**
   * Catches all the exceptions that the http app can fail with
   */
  final def catchAll[R1 <: R, E1, A1 <: A, B1 >: B](f: E => Http[R1, E1, A1, B1])(implicit
    @unused ev: CanFail[E],
  ): Http[R1, E1, A1, B1] =
    self.foldM(f, Http.succeed, Http.empty)

  /**
   * Collects some of the results of the http and converts it to another type.
   */
  final def collect[R1 <: R, E1 >: E, A1 <: A, B1 >: B, C](pf: PartialFunction[B1, C]): Http[R1, E1, A1, C] =
    self >>> Http.collect(pf)

  /**
   * Collects some of the results of the http and effectfully converts it to another type.
   */
  final def collectM[R1 <: R, E1 >: E, A1 <: A, B1 >: B, C](
    pf: PartialFunction[B1, ZIO[R1, E1, C]],
  ): Http[R1, E1, A1, C] =
    self >>> Http.collectM(pf)

  /**
   * Named alias for `<<<`
   */
  final def compose[R1 <: R, E1 >: E, A1 <: A, C1](other: Http[R1, E1, C1, A1]): Http[R1, E1, C1, B] =
    other andThen self

  /**
   * Transforms the input of the http before passing it on to the current Http
   */
  final def contraFlatMap[X]: MkContraFlatMap[R, E, A, B, X] = MkContraFlatMap[R, E, A, B, X](self)

  /**
   * Transforms the input of the http before passing it on to the current Http
   */
  final def contramap[X](xa: X => A): Http[R, E, X, B] = Http.identity[X].map(xa) >>> self

  /**
   * Transforms the input of the http before giving it effectfully
   */
  final def contramapM[R1 <: R, E1 >: E, X](xa: X => ZIO[R1, E1, A]): Http[R1, E1, X, B] =
    Http.fromEffectFunction[X](xa) >>> self

  /**
   * Named alias for `+++`
   */
  final def defaultWith[R1 <: R, E1 >: E, A1 <: A, B1 >: B](other: Http[R1, E1, A1, B1]): Http[R1, E1, A1, B1] =
    self.foldM(Http.fail, Http.succeed, other)

  /**
   * Delays production of output B for the specified duration of time
   */
  final def delayAfter(duration: Duration): Http[R with Clock, E, A, B] = self.mapM(b => UIO(b).delay(duration))

  /**
   * Delays consumption of input A for the specified duration of time
   */
  final def delayBefore(duration: Duration): Http[R with Clock, E, A, B] = self.contramapM(a => UIO(a).delay(duration))

  /**
   * Creates a new Http app from another
   */
  final def flatMap[R1 <: R, E1 >: E, A1 <: A, C1](f: B => Http[R1, E1, A1, C1]): Http[R1, E1, A1, C1] = {
    self.foldM(Http.fail, f, Http.empty)
  }

  /**
   * Flattens an Http app of an Http app
   */
  final def flatten[R1 <: R, E1 >: E, A1 <: A, B1](implicit
    ev: B <:< Http[R1, E1, A1, B1],
  ): Http[R1, E1, A1, B1] = self.flatMap(scala.Predef.identity(_))

  /**
   * Folds over the http app by taking in two functions one for success and one for failure respectively.
   */
  final def foldM[R1 <: R, A1 <: A, E1, B1](
    ee: E => Http[R1, E1, A1, B1],
    bb: B => Http[R1, E1, A1, B1],
    dd: Http[R1, E1, A1, B1],
  ): Http[R1, E1, A1, B1] = Http.FoldM(self, ee, bb, dd)

  /**
   * Transforms the output of the http app
   */
  final def map[C](bc: B => C): Http[R, E, A, C] = self.flatMap(b => Http.succeed(bc(b)))

  /**
   * Transforms the failure of the http app
   */
  final def mapError[E1](ee: E => E1): Http[R, E1, A, B] = self.foldM(e => Http.fail(ee(e)), Http.succeed, Http.empty)

  /**
   * Transforms the output of the http effectfully
   */
  final def mapM[R1 <: R, E1 >: E, C](bFc: B => ZIO[R1, E1, C]): Http[R1, E1, A, C] =
    self >>> Http.fromEffectFunction(bFc)

  /**
   * Attaches the provided middleware to the HttpApp
   */
  final def middleware[R1 <: R, E1 >: E, A1 <: A, B1 >: B](mid: HttpMiddleware[R1, E1])(implicit
    ev: IsHttpApp[R1, E1, A1, B1],
  ): HttpApp[R1, E1] =
    mid(self.asApp)

  /**
   * Named alias for `<>`
   */
  final def orElse[R1 <: R, E1, A1 <: A, B1 >: B](other: Http[R1, E1, A1, B1]): Http[R1, E1, A1, B1] =
    self.catchAll(_ => other)

  /**
   * Patches the response produced by the app
   */
  final def patch(patch: Patch)(implicit evB: IsResponse[R, E, B]): Http[R, E, A, Response[R, E]] =
    self.withResponse.map(patch(_))

  final def withResponse(implicit ev: IsResponse[R, E, B]): Http[R, E, A, Response[R, E]] =
    self.asInstanceOf[Http[R, E, A, Response[R, E]]]

  final def withRequest(implicit ev: IsRequest[A]): Http[R, E, Request, B] =
    self.asInstanceOf[Http[R, E, Request, B]]

  /**
   * Provides the environment to Http.
   */
  final def provide(r: R)(implicit ev: NeedsEnv[R]): Http[Any, E, A, B] =
    Http.fromPartialFunction[A](a => self(a).provide(r))

  /**
   * Provide part of the environment to HTTP that is not part of ZEnv
   */
  final def provideCustomLayer[E1 >: E, R1 <: Has[_]](
    layer: ZLayer[ZEnv, E1, R1],
  )(implicit ev: ZEnv with R1 <:< R, tagged: Tag[R1]): Http[ZEnv, E1, A, B] =
    Http.fromPartialFunction[A](a => self(a).provideCustomLayer(layer.mapError(Option(_))))

  /**
   * Provides layer to Http.
   */
  final def provideLayer[E1 >: E, R0, R1](
    layer: ZLayer[R0, E1, R1],
  )(implicit ev1: R1 <:< R, ev2: NeedsEnv[R]): Http[R0, E1, A, B] =
    Http.fromPartialFunction[A](a => self(a).provideLayer(layer.mapError(Option(_))))

  /**
   * Provides some of the environment to Http.
   */
  final def provideSome[R1 <: R](r: R1 => R)(implicit ev: NeedsEnv[R]): Http[R1, E, A, B] =
    Http.fromPartialFunction[A](a => self(a).provideSome(r))

  /**
   * Provides some of the environment to Http leaving the remainder `R0`.
   */
  final def provideSomeLayer[R0 <: Has[_], R1 <: Has[_], E1 >: E](
    layer: ZLayer[R0, E1, R1],
  )(implicit ev: R0 with R1 <:< R, tagged: Tag[R1]): Http[R0, E1, A, B] =
    Http.fromPartialFunction[A](a => self(a).provideSomeLayer(layer.mapError(Option(_))))

  /**
   * Performs a race between two apps
   */
  final def race[R1 <: R, E1 >: E, A1 <: A, B1 >: B](other: Http[R1, E1, A1, B1]): Http[R1, E1, A1, B1] =
    Http.fromPartialFunction(a => self(a) raceFirst other(a))

  /**
   * Sets the status in the response produced by the app
   */
  final def setStatus[R1 <: R, E1 >: E, B1 >: B](status: Status)(implicit
    evB: IsResponse[R1, E1, B1],
  ): Http[R, E, A, Response[R, E]] =
    self.patch(Patch.setStatus(status))

  /**
   * Converts a failing Http into a non-failing one by handling the failure and converting it to a result if possible.
   */
  final def silent[E1 >: E, B1 >: B](implicit s: CanBeSilenced[E1, B1]): Http[R, Nothing, A, B1] =
    self.catchAll(e => Http.succeed(s.silent(e)))

  /**
   * Returns an Http that peeks at the success of this Http.
   */
  final def tap[R1 <: R, E1 >: E, A1 <: A](f: B => Http[R1, E1, Any, Any]): Http[R1, E1, A, B] =
    self.flatMap(v => f(v).as(v))

  /**
   * Returns an Http that peeks at the success, failed or empty value of this Http.
   */
  final def tapAll[R1 <: R, E1 >: E](
    f: E => Http[R1, E1, Any, Any],
    g: B => Http[R1, E1, Any, Any],
    h: Http[R1, E1, Any, Any],
  ): Http[R1, E1, A, B] =
    self.foldM(
      e => f(e) *> Http.fail(e),
      x => g(x) *> Http.succeed(x),
      h *> Http.empty,
    )

  /**
   * Returns an Http that effectfully peeks at the success, failed or empty value of this Http.
   */
  final def tapAllM[R1 <: R, E1 >: E](
    f: E => ZIO[R1, E1, Any],
    g: B => ZIO[R1, E1, Any],
    h: ZIO[R1, E1, Any],
  ): Http[R1, E1, A, B] =
    tapAll(
      e => Http.fromEffect(f(e)),
      x => Http.fromEffect(g(x)),
      Http.fromEffect(h),
    )

  /**
   * Returns an Http that peeks at the failure of this Http.
   */
  final def tapError[R1 <: R, E1 >: E](f: E => Http[R1, E1, Any, Any]): Http[R1, E1, A, B] =
    self.foldM(
      e => f(e) *> Http.fail(e),
      Http.succeed,
      Http.empty,
    )

  /**
   * Returns an Http that effectfully peeks at the failure of this Http.
   */
  final def tapErrorM[R1 <: R, E1 >: E](f: E => ZIO[R1, E1, Any]): Http[R1, E1, A, B] =
    self.tapError(e => Http.fromEffect(f(e)))

  /**
   * Returns an Http that effectfully peeks at the success of this Http.
   */
  final def tapM[R1 <: R, E1 >: E](f: B => ZIO[R1, E1, Any]): Http[R1, E1, A, B] =
    self.tap(v => Http.fromEffect(f(v)))

  final def toApp[R1 <: R, E1 >: E](implicit evA: Request <:< A, evB: B <:< Response[R1, E1]): HttpApp[R1, E1] =
    self.asInstanceOf[Http[R, E, Request, Response[R, E]]]

  /**
   * Unwraps an Http that returns a ZIO of Http
   */
  final def unwrap[R1 <: R, E1 >: E, C](implicit ev: B <:< ZIO[R1, E1, C]): Http[R1, E1, A, C] =
    self.flatMap(Http.fromEffect(_))

  /**
   * Widens the type of the output
   */
  final def widen[B1](implicit ev: B <:< B1): Http[R, E, A, B1] =
    self.asInstanceOf[Http[R, E, A, B1]]

  /**
   * Combines the two apps and returns the result of the one on the right
   */
  final def zipRight[R1 <: R, E1 >: E, A1 <: A, C1](other: Http[R1, E1, A1, C1]): Http[R1, E1, A1, C1] =
    self.flatMap(_ => other)

  final private[zhttp] def compile[R1 <: R, E1 >: E <: Throwable](
    zExec: HttpRuntime[R1],
    settings: Server.Config[R1, E1],
    serverTime: ServerTimeGenerator,
  )(implicit evApp: IsHttpApp[R1, E1, A, B]): ChannelHandler =
    Handler[R1, E1](self.asInstanceOf[HttpApp[R1, E1]], zExec, settings, serverTime)

  /**
   * Evaluates the app and returns an HExit that can be resolved further
   */
  final private[zhttp] def execute(a: A): HExit[R, E, B] = {
    self match {
      case Empty                   => HExit.empty
      case Identity                => HExit.succeed(a.asInstanceOf[B])
      case Succeed(b)              => HExit.succeed(b)
      case Fail(e)                 => HExit.fail(e)
      case FromEffectFunction(f)   => HExit.effect(f(a))
      case Collect(pf)             => if (pf.isDefinedAt(a)) HExit.succeed(pf(a)) else HExit.empty
      case Chain(self, other)      => HExit.suspend(self.execute(a) >>= (other.execute(_)))
      case FoldM(self, ee, bb, dd) =>
        HExit.suspend {
          self.execute(a).foldM(ee(_).execute(a), bb(_).execute(a), dd.execute(a))
        }
    }
  }
}

object Http {

  /**
   * Creates an HTTP app which accepts a request and produces response.
   */
  def collect[A]: Http.MakeCollect[A] = Http.MakeCollect(())

  /**
   * Creates an HTTP app which accepts a request and produces response effectfully.
   */
  def collectM[A]: Http.MakeCollectM[A] = Http.MakeCollectM(())

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
   * Creates an Http that always fails
   */
  def fail[E](e: E): Http[Any, E, Any, Nothing] = Http.Fail(e)

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
   * Converts a ZIO to an Http type
   */
  def fromEffect[R, E, B](effect: ZIO[R, E, B]): Http[R, E, Any, B] = Http.fromEffectFunction(_ => effect)

  /**
   * Creates an Http app from a function that returns a ZIO
   */
  def fromEffectFunction[A]: Http.MakeFromEffectFunction[A] = Http.MakeFromEffectFunction(())

  /**
   * Creates a Http from a pure function
   */
  def fromFunction[A]: FromFunction[A] = new FromFunction[A](())

  /**
   * Creates a Http from an effectful pure function
   */
  def fromFunctionM[A]: FromFunctionM[A] = new FromFunctionM[A](())

  /**
   * Creates an `Http` from a function that takes a value of type `A` and returns with a `ZIO[R, Option[E], B]`. The
   * returned effect can fail with a `None` to signal "not found" to the backend.
   */
  def fromPartialFunction[A]: FromPartialFunction[A] = new FromPartialFunction(())

  /**
   * Creates a pass thru Http instances
   */
  def identity[A]: Http[Any, Nothing, A, A] = Http.Identity

  /**
   * Creates an Http that delegates to other Https.
   */
  def route[A]: Http.MakeRoute[A] = Http.MakeRoute(())

  /**
   * Creates an Http that always returns the same response and never fails.
   */
  def succeed[B](b: B): Http[Any, Nothing, Any, B] = Http.Succeed(b)

  // Ctor Help
  final case class MakeCollectM[A](unit: Unit) extends AnyVal {
    def apply[R, E, B](pf: PartialFunction[A, ZIO[R, E, B]]): Http[R, E, A, B] =
      Http.collect[A] { case a if pf.isDefinedAt(a) => Http.fromEffect(pf(a)) }.flatten
  }

  final case class MakeCollect[A](unit: Unit) extends AnyVal {
    def apply[B](pf: PartialFunction[A, B]): Http[Any, Nothing, A, B] = Collect(pf)
  }

  final case class MakeFromEffectFunction[A](unit: Unit) extends AnyVal {
    def apply[R, E, B](f: A => ZIO[R, E, B]): Http[R, E, A, B] = Http.FromEffectFunction(f)
  }

  final case class MakeRoute[A](unit: Unit) extends AnyVal {
    def apply[R, E, B](pf: PartialFunction[A, Http[R, E, A, B]]): Http[R, E, A, B] =
      Http.collect[A] { case r if pf.isDefinedAt(r) => pf(r) }.flatten
  }

  final case class MkContraFlatMap[-R, +E, -A, +B, X](self: Http[R, E, A, B]) extends AnyVal {
    def apply[R1 <: R, E1 >: E](xa: X => Http[R1, E1, Any, A]): Http[R1, E1, X, B] =
      Http.identity[X].flatMap(xa) >>> self
  }

  final class FromPartialFunction[A](val unit: Unit) extends AnyVal {
    def apply[R, E, B](f: A => ZIO[R, Option[E], B]): Http[R, E, A, B] = Http
      .collectM[A] { case a =>
        f(a).map(Http.succeed(_)).catchAll {
          case Some(error) => UIO(Http.fail(error))
          case None        => UIO(Http.empty)
        }
      }
      .flatten
  }

  final class FromFunction[A](val unit: Unit) extends AnyVal {
    def apply[B](f: A => B): Http[Any, Nothing, A, B] = Http.identity[A].map(f)
  }

  final class FromFunctionM[A](val unit: Unit) extends AnyVal {
    def apply[R, E, B](f: A => ZIO[R, E, B]): Http[R, E, A, B] = Http.identity[A].mapM(f)
  }

  private final case class Succeed[B](b: B) extends Http[Any, Nothing, Any, B]

  private final case class Fail[E](e: E) extends Http[Any, E, Any, Nothing]

  private final case class FromEffectFunction[R, E, A, B](f: A => ZIO[R, E, B]) extends Http[R, E, A, B]

  private final case class Collect[R, E, A, B](ab: PartialFunction[A, B]) extends Http[R, E, A, B]

  private final case class Chain[R, E, A, B, C](self: Http[R, E, A, B], other: Http[R, E, B, C])
      extends Http[R, E, A, C]

  private final case class FoldM[R, E, EE, A, B, BB](
    self: Http[R, E, A, B],
    ee: E => Http[R, EE, A, BB],
    bb: B => Http[R, EE, A, BB],
    dd: Http[R, EE, A, BB],
  ) extends Http[R, EE, A, BB]

  private case object Empty extends Http[Any, Nothing, Any, Nothing]

  private case object Identity extends Http[Any, Nothing, Any, Nothing]
}
