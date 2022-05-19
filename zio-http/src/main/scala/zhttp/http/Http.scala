package zhttp.http

import io.netty.buffer.{ByteBuf, ByteBufUtil}
import io.netty.channel.{ChannelHandler, ChannelHandlerContext}
import io.netty.handler.codec.http.HttpHeaderNames
import zhttp.html._
import zhttp.http.headers.HeaderModifier
import zhttp.service.{Handler, HttpRuntime, Server, ServerResponseWriter}
import zio._
import zio.blocking.{Blocking, effectBlocking}
import zio.clock.Clock
import zio.duration.Duration
import zio.stream.ZStream

import java.io.{File, IOException}
import java.net
import java.net.{InetAddress, InetSocketAddress}
import java.nio.charset.Charset
import java.nio.file.Paths
import scala.annotation.unused
import scala.reflect.ClassTag
import scala.util.control.NonFatal

/**
 * A functional domain to model Http apps using ZIO and that can work over any
 * kind of request and response types.
 */
sealed trait Http[-R, +E, -A, +B] extends (A => ZIO[R, Option[E], B]) { self =>

  import Http._

  /**
   * Extracts body as a ByteBuf
   */
  private[zhttp] final def bodyAsByteBuf(implicit
    eb: B <:< Response,
    ee: E <:< Throwable,
  ): Http[R, Throwable, A, ByteBuf] =
    self.widen[Throwable, B].mapZIO(_.bodyAsByteBuf)

  /**
   * Evaluates the app and returns an HExit that can be resolved further
   *
   * NOTE: `execute` is not a stack-safe method for performance reasons. Unlike
   * ZIO, there is no reason why the execute should be stack safe. The
   * performance improves quite significantly if no additional heap allocations
   * are required this way.
   */
  final private[zhttp] def execute(a: A): HExit[R, E, B] =
    self match {

      case Http.Empty                     => HExit.empty
      case Http.Identity                  => HExit.succeed(a.asInstanceOf[B])
      case Succeed(b)                     => HExit.succeed(b)
      case Fail(e)                        => HExit.fail(e)
      case Die(e)                         => HExit.die(e)
      case Attempt(a)                     =>
        try { HExit.succeed(a()) }
        catch { case e: Throwable => HExit.fail(e.asInstanceOf[E]) }
      case FromFunctionHExit(f)           =>
        try { f(a) }
        catch { case e: Throwable => HExit.die(e) }
      case FromHExit(h)                   => h
      case Chain(self, other)             => self.execute(a).flatMap(b => other.execute(b))
      case Race(self, other)              =>
        (self.execute(a), other.execute(a)) match {
          case (HExit.Effect(self), HExit.Effect(other)) =>
            Http.fromOptionFunction[Any](_ => self.raceFirst(other)).execute(a)
          case (HExit.Effect(_), other)                  => other
          case (self, _)                                 => self
        }
      case FoldHttp(self, ee, df, bb, dd) =>
        try {
          self.execute(a).foldExit(ee(_).execute(a), df(_).execute(a), bb(_).execute(a), dd.execute(a))
        } catch {
          case e: Throwable => HExit.die(e)
        }

      case RunMiddleware(app, mid) =>
        try {
          mid(app).execute(a)
        } catch {
          case e: Throwable => HExit.die(e)
        }

      case When(f, other) =>
        try {
          if (f(a)) other.execute(a) else HExit.empty
        } catch {
          case e: Throwable => HExit.die(e)
        }

      case Combine(self, other) => {
        self.execute(a) match {
          case HExit.Empty            => other.execute(a)
          case exit: HExit.Success[_] => exit.asInstanceOf[HExit[R, E, B]]
          case exit: HExit.Failure[_] => exit.asInstanceOf[HExit[R, E, B]]
          case exit: HExit.Die        => exit
          case exit @ HExit.Effect(_) => exit.defaultWith(other.execute(a)).asInstanceOf[HExit[R, E, B]]
        }
      }
    }

  /**
   * Attaches the provided middleware to the Http app
   */
  final def @@[R1 <: R, E1 >: E, A1 <: A, B1 >: B, A2, B2](
    mid: Middleware[R1, E1, A1, B1, A2, B2],
  ): Http[R1, E1, A2, B2] = mid(self)

  /**
   * Combines two Http instances into a middleware that works a codec for
   * incoming and outgoing messages.
   */
  final def \/[R1 <: R, E1 >: E, C, D](other: Http[R1, E1, C, D]): Middleware[R1, E1, B, C, A, D] =
    self codecMiddleware other

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
  final def ++[R1 <: R, E1 >: E, A1 <: A, B1 >: B](other: Http[R1, E1, A1, B1]): Http[R1, E1, A1, B1] =
    self defaultWith other

  /**
   * Alias for zipRight
   */
  final def *>[R1 <: R, E1 >: E, A1 <: A, C1](other: Http[R1, E1, A1, C1]): Http[R1, E1, A1, C1] =
    self.zipRight(other)

  /**
   * Returns an http app that submerges the error case of an `Either` into the
   * `Http`. The inverse operation of `Http.either`.
   */
  final def absolve[E1 >: E, C](implicit ev: B <:< Either[E1, C]): Http[R, E1, A, C] =
    self.flatMap(b =>
      ev(b) match {
        case Right(c) => Http.succeed(c)
        case Left(e)  => Http.fail(e)
      },
    )

  /**
   * Named alias for `>>>`
   */
  final def andThen[R1 <: R, E1 >: E, B1 >: B, C](other: Http[R1, E1, B1, C]): Http[R1, E1, A, C] =
    Http.Chain(self, other)

  /**
   * Consumes the input and executes the Http.
   */
  final def apply(a: A): ZIO[R, Option[E], B] = execute(a).toZIO

  /**
   * Makes the app resolve with a constant value
   */
  final def as[C](c: C): Http[R, E, A, C] =
    self *> Http.succeed(c)

  /**
   * Extracts body
   */
  final def body(implicit eb: B <:< Response, ee: E <:< Throwable): Http[R, Throwable, A, Chunk[Byte]] =
    self.bodyAsByteBuf.mapZIO(buf => Task(Chunk.fromArray(ByteBufUtil.getBytes(buf))))

  /**
   * Extracts body as a string
   */
  final def bodyAsString(implicit eb: B <:< Response, ee: E <:< Throwable): Http[R, Throwable, A, String] =
    self.bodyAsByteBuf.mapZIO(bytes => Task(bytes.toString(HTTP_CHARSET)))

  /**
   * Catches all the exceptions that the http app can fail with
   */
  final def catchAll[R1 <: R, E1, A1 <: A, B1 >: B](f: E => Http[R1, E1, A1, B1])(implicit
    @unused ev: CanFail[E],
  ): Http[R1, E1, A1, B1] =
    self.foldHttp(f, Http.die, Http.succeed, Http.empty)

  /**
   * Recovers from all defects with provided function.
   *
   * '''WARNING''': There is no sensible way to recover from defects. This
   * method should be used only at the boundary between `Http` and an external
   * system, to transmit information on a defect for diagnostic or explanatory
   * purposes.
   */
  final def catchAllDefect[R2 <: R, E2 >: E, A2 <: A, B2 >: B](
    h: Throwable => Http[R2, E2, A2, B2],
  ): Http[R2, E2, A2, B2] =
    self.catchSomeDefect { case t => h(t) }

  /**
   * Recovers from all NonFatal Throwables.
   */
  final def catchNonFatalOrDie[R2 <: R, E2 >: E, A2 <: A, B2 >: B](
    h: E => Http[R2, E2, A2, B2],
  )(implicit ev1: CanFail[E], ev2: E <:< Throwable): Http[R2, E2, A2, B2] =
    self.catchSome {
      case e @ NonFatal(_) => h(e)
      case e               => Http.die(e)
    }

  /**
   * Recovers from some or all of the error cases.
   */
  final def catchSome[R1 <: R, E1 >: E, A1 <: A, B1 >: B](f: PartialFunction[E, Http[R1, E1, A1, B1]])(implicit
    ev: CanFail[E],
  ): Http[R1, E1, A1, B1] =
    self.catchAll(e => f.applyOrElse(e, Http.fail[E1]))

  /**
   * Recovers from some or all of the defects with provided partial function.
   *
   * '''WARNING''': There is no sensible way to recover from defects. This
   * method should be used only at the boundary between `Http` and an external
   * system, to transmit information on a defect for diagnostic or explanatory
   * purposes.
   */
  final def catchSomeDefect[R1 <: R, E1 >: E, A1 <: A, B1 >: B](
    pf: PartialFunction[Throwable, Http[R1, E1, A1, B1]],
  ): Http[R1, E1, A1, B1] =
    unrefineWith(pf)(Http.fail).catchAll(e => e)

  /**
   * Combines two Http instances into a middleware that works a codec for
   * incoming and outgoing messages.
   */
  final def codecMiddleware[R1 <: R, E1 >: E, C, D](other: Http[R1, E1, C, D]): Middleware[R1, E1, B, C, A, D] =
    Middleware.codecHttp(self, other)

  /**
   * Collects some of the results of the http and converts it to another type.
   */
  final def collect[R1 <: R, E1 >: E, A1 <: A, B1 >: B, C](pf: PartialFunction[B1, C]): Http[R1, E1, A1, C] =
    self >>> Http.collect(pf)

  final def collectManaged[R1 <: R, E1 >: E, A1 <: A, B1 >: B, C](
    pf: PartialFunction[B1, ZManaged[R1, E1, C]],
  ): Http[R1, E1, A1, C] =
    self >>> Http.collectManaged(pf)

  /**
   * Collects some of the results of the http and effectfully converts it to
   * another type.
   */
  final def collectZIO[R1 <: R, E1 >: E, A1 <: A, B1 >: B, C](
    pf: PartialFunction[B1, ZIO[R1, E1, C]],
  ): Http[R1, E1, A1, C] =
    self >>> Http.collectZIO(pf)

  /**
   * Named alias for `<<<`
   */
  final def compose[R1 <: R, E1 >: E, A1 <: A, C1](other: Http[R1, E1, C1, A1]): Http[R1, E1, C1, B] =
    other andThen self

  /**
   * Extracts content-length from the response if available
   */
  final def contentLength(implicit eb: B <:< Response): Http[R, E, A, Option[Long]] =
    headers.map(_.contentLength)

  /**
   * Extracts the value of ContentType header
   */
  final def contentType(implicit eb: B <:< Response): Http[R, E, A, Option[CharSequence]] =
    headerValue(HttpHeaderNames.CONTENT_TYPE)

  /**
   * Transforms the input of the http before passing it on to the current Http
   */
  final def contraFlatMap[X]: PartialContraFlatMap[R, E, A, B, X] = PartialContraFlatMap[R, E, A, B, X](self)

  /**
   * Transforms the input of the http before passing it on to the current Http
   */
  final def contramap[X](xa: X => A): Http[R, E, X, B] = Http.identity[X].map(xa) >>> self

  /**
   * Transforms the input of the http before giving it effectfully
   */
  final def contramapZIO[R1 <: R, E1 >: E, X](xa: X => ZIO[R1, E1, A]): Http[R1, E1, X, B] =
    Http.fromFunctionZIO[X](xa) >>> self

  /**
   * Named alias for `++`
   */
  final def defaultWith[R1 <: R, E1 >: E, A1 <: A, B1 >: B](other: Http[R1, E1, A1, B1]): Http[R1, E1, A1, B1] =
    Http.Combine(self, other)

  /**
   * Delays production of output B for the specified duration of time
   */
  final def delay(duration: Duration): Http[R with Clock, E, A, B] = self.delayAfter(duration)

  /**
   * Delays production of output B for the specified duration of time
   */
  final def delayAfter(duration: Duration): Http[R with Clock, E, A, B] = self.mapZIO(b => UIO(b).delay(duration))

  /**
   * Delays consumption of input A for the specified duration of time
   */
  final def delayBefore(duration: Duration): Http[R with Clock, E, A, B] =
    self.contramapZIO(a => UIO(a).delay(duration))

  /**
   * Returns an http app whose failure and success have been lifted into an
   * `Either`. The resulting app cannot fail, because the failure case has been
   * exposed as part of the `Either` success case.
   */
  final def either(implicit ev: CanFail[E]): Http[R, Nothing, A, Either[E, B]] =
    self.foldHttp(
      e => Http.succeed(Left(e)),
      Http.die,
      b => Http.succeed(Right(b)),
      Http.empty,
    )

  /**
   * Creates a new Http app from another
   */
  final def flatMap[R1 <: R, E1 >: E, A1 <: A, C1](f: B => Http[R1, E1, A1, C1]): Http[R1, E1, A1, C1] = {
    self.foldHttp(Http.fail, Http.die, f, Http.empty)
  }

  /**
   * Flattens an Http app of an Http app
   */
  final def flatten[R1 <: R, E1 >: E, A1 <: A, B1](implicit
    ev: B <:< Http[R1, E1, A1, B1],
  ): Http[R1, E1, A1, B1] = {
    self.flatMap(scala.Predef.identity(_))
  }

  /**
   * Folds over the http app by taking in two functions one for success and one
   * for failure respectively.
   */
  final def foldHttp[R1 <: R, A1 <: A, E1, B1](
    failure: E => Http[R1, E1, A1, B1],
    defect: Throwable => Http[R1, E1, A1, B1],
    success: B => Http[R1, E1, A1, B1],
    empty: Http[R1, E1, A1, B1],
  ): Http[R1, E1, A1, B1] = Http.FoldHttp(self, failure, defect, success, empty)

  /**
   * Extracts the value of the provided header name.
   */
  final def headerValue(name: CharSequence)(implicit eb: B <:< Response): Http[R, E, A, Option[CharSequence]] =
    headers.map(_.headerValue(name))

  /**
   * Extracts the `Headers` from the type `B` if possible
   */
  final def headers(implicit eb: B <:< Response): Http[R, E, A, Headers] = self.map(_.headers)

  /**
   * Transforms the output of the http app
   */
  final def map[C](bc: B => C): Http[R, E, A, C] = self.flatMap(b => Http.succeed(bc(b)))

  /**
   * Transforms the failure of the http app
   */
  final def mapError[E1](ee: E => E1): Http[R, E1, A, B] =
    self.foldHttp(e => Http.fail(ee(e)), Http.die, Http.succeed, Http.empty)

  /**
   * Transforms the output of the http effectfully
   */
  final def mapZIO[R1 <: R, E1 >: E, C](bFc: B => ZIO[R1, E1, C]): Http[R1, E1, A, C] =
    self >>> Http.fromFunctionZIO(bFc)

  /**
   * Returns a new Http where the error channel has been merged into the success
   * channel to their common combined type.
   */
  final def merge[E1 >: E, B1 >: B](implicit ev: E1 =:= B1): Http[R, Nothing, A, B1] =
    self.catchAll(Http.succeed(_))

  /**
   * Named alias for @@
   */
  final def middleware[R1 <: R, E1 >: E, A1 <: A, B1 >: B, A2, B2](
    mid: Middleware[R1, E1, A1, B1, A2, B2],
  ): Http[R1, E1, A2, B2] = Http.RunMiddleware(self, mid)

  /**
   * Narrows the type of the input
   */
  final def narrow[A1](implicit a: A1 <:< A): Http[R, E, A1, B] =
    self.asInstanceOf[Http[R, E, A1, B]]

  /**
   * Executes this app, skipping the error but returning optionally the success.
   */
  final def option(implicit ev: CanFail[E]): Http[R, Nothing, A, Option[B]] =
    self.foldHttp(
      _ => Http.succeed(None),
      Http.die,
      b => Http.succeed(Some(b)),
      Http.empty,
    )

  /**
   * Converts an option on errors into an option on values.
   */
  final def optional[E1](implicit ev: E <:< Option[E1]): Http[R, E1, A, Option[B]] =
    self.foldHttp(
      ev(_) match {
        case Some(e) => Http.fail(e)
        case None    => Http.succeed(None)
      },
      Http.die,
      b => Http.succeed(Some(b)),
      Http.empty,
    )

  /**
   * Translates app failure into death of the app, making all failures unchecked
   * and not a part of the type of the app.
   */
  final def orDie(implicit ev1: E <:< Throwable, ev2: CanFail[E]): Http[R, Nothing, A, B] =
    orDieWith(ev1)

  /**
   * Keeps none of the errors, and terminates the http app with them, using the
   * specified function to convert the `E` into a `Throwable`.
   */
  final def orDieWith(f: E => Throwable)(implicit ev: CanFail[E]): Http[R, Nothing, A, B] =
    self.foldHttp(e => Http.die(f(e)), Http.die, Http.succeed, Http.empty)

  /**
   * Named alias for `<>`
   */
  final def orElse[R1 <: R, E1, A1 <: A, B1 >: B](other: Http[R1, E1, A1, B1]): Http[R1, E1, A1, B1] =
    self.catchAll(_ => other)

  /**
   * Provide part of the environment to HTTP that is not part of ZEnv
   */
  final def provideCustomLayer[E1 >: E, R1 <: Has[_]](
    layer: ZLayer[ZEnv, E1, R1],
  )(implicit ev: ZEnv with R1 <:< R, tagged: Tag[R1]): Http[ZEnv, E1, A, B] =
    Http.fromOptionFunction[A](a => self(a).provideCustomLayer(layer.mapError(Option(_))))

  /**
   * Provides the environment to Http.
   */
  final def provideEnvironment(r: R)(implicit ev: NeedsEnv[R]): Http[Any, E, A, B] =
    Http.fromOptionFunction[A](a => self(a).provide(r))

  /**
   * Provides layer to Http.
   */
  final def provideLayer[E1 >: E, R0, R1](
    layer: ZLayer[R0, E1, R1],
  )(implicit ev1: R1 <:< R, ev2: NeedsEnv[R]): Http[R0, E1, A, B] =
    Http.fromOptionFunction[A](a => self(a).provideLayer(layer.mapError(Option(_))))

  /**
   * Provides some of the environment to Http.
   */
  final def provideSomeEnvironment[R1 <: R](r: R1 => R)(implicit ev: NeedsEnv[R]): Http[R1, E, A, B] =
    Http.fromOptionFunction[A](a => self(a).provideSome(r))

  /**
   * Provides some of the environment to Http leaving the remainder `R0`.
   */
  final def provideSomeLayer[R0 <: Has[_], R1 <: Has[_], E1 >: E](
    layer: ZLayer[R0, E1, R1],
  )(implicit ev: R0 with R1 <:< R, tagged: Tag[R1]): Http[R0, E1, A, B] =
    Http.fromOptionFunction[A](a => self(a).provideSomeLayer(layer.mapError(Option(_))))

  /**
   * Performs a race between two apps
   */
  final def race[R1 <: R, E1 >: E, A1 <: A, B1 >: B](other: Http[R1, E1, A1, B1]): Http[R1, E1, A1, B1] =
    Http.Race(self, other)

  /**
   * Keeps some of the errors, and terminates the http app with the rest.
   */
  final def refineOrDie[E1](
    pf: PartialFunction[E, E1],
  )(implicit ev1: E <:< Throwable, ev2: CanFail[E]): Http[R, E1, A, B] =
    refineOrDieWith(pf)(ev1)

  /**
   * Keeps some of the errors, and terminates the http app with the rest, using
   * the specified function to convert the `E` into a `Throwable`.
   */
  final def refineOrDieWith[E1](pf: PartialFunction[E, E1])(f: E => Throwable)(implicit
    ev: CanFail[E],
  ): Http[R, E1, A, B] =
    self.catchAll(err => (pf lift err).fold[Http[R, E1, A, B]](Http.die(f(err)))(Http.fail))

  /**
   * Extracts `Status` from the type `B` is possible.
   */
  final def status(implicit ev: B <:< Response): Http[R, E, A, Status] = self.map(_.status)

  /**
   * Returns an Http that peeks at the success of this Http.
   */
  final def tap[R1 <: R, E1 >: E, A1 <: A](f: B => Http[R1, E1, Any, Any]): Http[R1, E1, A, B] =
    self.flatMap(v => f(v).as(v))

  /**
   * Returns an Http that peeks at the success, failed, defective or empty value
   * of this Http.
   */
  final def tapAll[R1 <: R, E1 >: E](
    failure: E => Http[R1, E1, Any, Any],
    defect: Throwable => Http[R1, E1, Any, Any],
    success: B => Http[R1, E1, Any, Any],
    empty: Http[R1, E1, Any, Any],
  ): Http[R1, E1, A, B] =
    self.foldHttp(
      e => failure(e) *> Http.fail(e),
      d => defect(d) *> Http.die(d),
      x => success(x) *> Http.succeed(x),
      empty *> Http.empty,
    )

  /**
   * Returns an Http that effectfully peeks at the success, failed, defective or
   * empty value of this Http.
   */
  final def tapAllZIO[R1 <: R, E1 >: E](
    failure: E => ZIO[R1, E1, Any],
    defect: Throwable => ZIO[R1, E1, Any],
    success: B => ZIO[R1, E1, Any],
    empty: ZIO[R1, E1, Any],
  ): Http[R1, E1, A, B] =
    tapAll(
      e => Http.fromZIO(failure(e)),
      d => Http.fromZIO(defect(d)),
      x => Http.fromZIO(success(x)),
      Http.fromZIO(empty),
    )

  /**
   * Returns an Http that peeks at the failure of this Http.
   */
  final def tapError[R1 <: R, E1 >: E](f: E => Http[R1, E1, Any, Any]): Http[R1, E1, A, B] =
    self.foldHttp(
      e => f(e) *> Http.fail(e),
      Http.die,
      Http.succeed,
      Http.empty,
    )

  /**
   * Returns an Http that effectfully peeks at the failure of this Http.
   */
  final def tapErrorZIO[R1 <: R, E1 >: E](f: E => ZIO[R1, E1, Any]): Http[R1, E1, A, B] =
    self.tapError(e => Http.fromZIO(f(e)))

  /**
   * Returns an Http that effectfully peeks at the success of this Http.
   */
  final def tapZIO[R1 <: R, E1 >: E](f: B => ZIO[R1, E1, Any]): Http[R1, E1, A, B] =
    self.tap(v => Http.fromZIO(f(v)))

  /**
   * Takes some defects and converts them into failures.
   */
  final def unrefine[E1 >: E](pf: PartialFunction[Throwable, E1]): Http[R, E1, A, B] =
    unrefineWith(pf)(e => e)

  /**
   * Takes some defects and converts them into failures.
   */
  final def unrefineTo[E1 >: E: ClassTag]: Http[R, E1, A, B] =
    unrefine { case e: E1 => e }

  /**
   * Takes some defects and converts them into failures, using the specified
   * function to convert the `E` into an `E1`.
   */
  final def unrefineWith[E1](pf: PartialFunction[Throwable, E1])(f: E => E1): Http[R, E1, A, B] =
    self.foldHttp(
      e => Http.fail(f(e)),
      d => if (pf.isDefinedAt(d)) Http.fail(pf(d)) else Http.die(d),
      Http.succeed,
      Http.empty,
    )

  /**
   * Unwraps an Http that returns a ZIO of Http
   */
  final def unwrap[R1 <: R, E1 >: E, C](implicit ev: B <:< ZIO[R1, E1, C]): Http[R1, E1, A, C] =
    self.flatMap(Http.fromZIO(_))

  /**
   * Applies Http based only if the condition function evaluates to true
   */
  final def when[A2 <: A](f: A2 => Boolean): Http[R, E, A2, B] =
    Http.When(f, self)

  /**
   * Widens the type of the output
   */
  final def widen[E1, B1](implicit e: E <:< E1, b: B <:< B1): Http[R, E1, A, B1] =
    self.asInstanceOf[Http[R, E1, A, B1]]

  /**
   * Combines the two apps and returns the result of the one on the right
   */
  final def zipRight[R1 <: R, E1 >: E, A1 <: A, C1](other: Http[R1, E1, A1, C1]): Http[R1, E1, A1, C1] =
    self.flatMap(_ => other)
}

object Http {

  implicit final class HttpAppSyntax[-R, +E](val http: HttpApp[R, E]) extends HeaderModifier[HttpApp[R, E]] {
    self =>

    private[zhttp] def compile[R1 <: R](
      zExec: HttpRuntime[R1],
      settings: Server.Config[R1, Throwable],
      resWriter: ServerResponseWriter[R1],
    )(implicit
      evE: E <:< Throwable,
    ): ChannelHandler =
      Handler(http.asInstanceOf[HttpApp[R1, Throwable]], zExec, settings, resWriter)

    /**
     * Patches the response produced by the app
     */
    def patch(patch: Patch): HttpApp[R, E] = http.map(patch(_))

    /**
     * Overwrites the method in the incoming request
     */
    def setMethod(method: Method): HttpApp[R, E] = http.contramap[Request](_.setMethod(method))

    /**
     * Overwrites the path in the incoming request
     */
    def setPath(path: Path): HttpApp[R, E] = http.contramap[Request](_.setPath(path))

    /**
     * Sets the status in the response produced by the app
     */
    def setStatus(status: Status): HttpApp[R, E] = patch(Patch.setStatus(status))

    /**
     * Overwrites the url in the incoming request
     */
    def setUrl(url: URL): HttpApp[R, E] = http.contramap[Request](_.setUrl(url))

    /**
     * Updates the response headers using the provided function
     */
    override def updateHeaders(update: Headers => Headers): HttpApp[R, E] = http.map(_.updateHeaders(update))

    /**
     * Applies Http based on the path
     */
    def whenPathEq(p: Path): HttpApp[R, E] = http.whenPathEq(p.toString)

    /**
     * Applies Http based on the path as string
     */
    def whenPathEq(p: String): HttpApp[R, E] = http.when(_.unsafeEncode.uri().contentEquals(p))
  }

  /**
   * Equivalent to `Http.succeed`
   */
  def apply[B](b: B): Http[Any, Nothing, Any, B] = Http.succeed(b)

  /**
   * Attempts to create an Http that succeeds with the provided value, capturing
   * all exceptions on it's way.
   */
  def attempt[A](a: => A): Http[Any, Throwable, Any, A] = Attempt(() => a)

  /**
   * Creates an HTTP app which always responds with a 400 status code.
   */
  def badRequest(msg: String): HttpApp[Any, Nothing] = Http.error(HttpError.BadRequest(msg))

  /**
   * Creates an HTTP app which accepts a request and produces response.
   */
  def collect[A]: Http.PartialCollect[A] = Http.PartialCollect(())

  /**
   * Create an HTTP app from a partial function from A to HExit[R,E,B]
   */
  def collectHExit[A]: Http.PartialCollectHExit[A] = Http.PartialCollectHExit(())

  /**
   * Create an HTTP app from a partial function from A to Http[R,E,A,B]
   */
  def collectHttp[A]: Http.PartialCollectHttp[A] = Http.PartialCollectHttp(())

  /**
   * Creates an Http app which accepts a request and produces response from a
   * managed resource
   */
  def collectManaged[A]: Http.PartialCollectManaged[A] = Http.PartialCollectManaged(())

  /**
   * Creates an HTTP app which accepts a request and produces response
   * effectfully.
   */
  def collectZIO[A]: Http.PartialCollectZIO[A] = Http.PartialCollectZIO(())

  /**
   * Combines multiple Http apps into one
   */
  def combine[R, E, A, B](i: Iterable[Http[R, E, A, B]]): Http[R, E, A, B] =
    i.reduce(_.defaultWith(_))

  /**
   * Provides access to the request's ChannelHandlerContext
   */
  def context: Http[Any, Nothing, Request, ChannelHandlerContext] = Http.fromFunction[Request](_.unsafeContext)

  /**
   * Returns an http app that dies with the specified `Throwable`. This method
   * can be used for terminating an app because a defect has been detected in
   * the code. Terminating an http app leads to aborting handling of an HTTP
   * request and responding with 500 Internal Server Error.
   */
  def die(t: Throwable): UHttp[Any, Nothing] =
    Http.Die(t)

  /**
   * Returns an app that dies with a `RuntimeException` having the specified
   * text message. This method can be used for terminating a HTTP request
   * because a defect has been detected in the code.
   */
  def dieMessage(message: => String): UHttp[Any, Nothing] =
    die(new RuntimeException(message))

  /**
   * Creates an empty Http value
   */
  def empty: Http[Any, Nothing, Any, Nothing] = Http.Empty

  /**
   * Creates an HTTP app with HttpError.
   */
  def error(cause: HttpError): HttpApp[Any, Nothing] = Http.response(Response.fromHttpError(cause))

  /**
   * Creates an Http app that responds with 500 status code
   */
  def error(msg: String): HttpApp[Any, Nothing] = Http.error(HttpError.InternalServerError(msg))

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
  def flattenZIO[R, E, A, B](http: Http[R, E, A, ZIO[R, E, B]]): Http[R, E, A, B] =
    http.flatMap(Http.fromZIO)

  /**
   * Creates an Http app that responds with 403 - Forbidden status code
   */
  def forbidden(msg: String): HttpApp[Any, Nothing] = Http.error(HttpError.Forbidden(msg))

  /**
   * Creates an Http app which always responds the provided data and a 200
   * status code
   */
  def fromData(data: HttpData): HttpApp[Any, Nothing] = response(Response(data = data))

  /**
   * Lifts an `Either` into a `Http` value.
   */
  def fromEither[E, A](v: Either[E, A]): Http[Any, E, Any, A] =
    v.fold(Http.fail, Http.succeed)

  /**
   * Creates an Http app from the contents of a file.
   */
  def fromFile(file: => java.io.File): HttpApp[Any, Throwable] = Http.fromFileZIO(UIO(file))

  /**
   * Creates an Http app from the contents of a file which is produced from an
   * effect. The operator automatically adds the content-length and content-type
   * headers if possible.
   */
  def fromFileZIO[R](fileZIO: ZIO[R, Throwable, java.io.File]): HttpApp[R, Throwable] = {
    val response: ZIO[R, Throwable, HttpApp[R, Throwable]] =
      fileZIO.flatMap { file =>
        Task {
          if (file.isFile) {
            val length   = Headers.contentLength(file.length())
            val response = Response(headers = length, data = HttpData.fromFile(file))
            val pathName = file.toPath.toString

            // Extract file extension
            val ext = pathName.lastIndexOf(".") match {
              case -1 => None
              case i  => Some(pathName.substring(i + 1))
            }

            // Set MIME type in the response headers. This is only relevant in
            // case of RandomAccessFile transfers as browsers use the MIME type,
            // not the file extension, to determine how to process a URL.
            // {{{<a href="MSDN Doc">https://developer.mozilla.org/en-US/docs/Web/HTTP/Headers/Content-Type</a>}}}
            Http.succeed(ext.flatMap(MediaType.forFileExtension).fold(response)(response.withMediaType))
          } else Http.empty
        }
      }

    Http.fromZIO(response).flatten
  }

  /**
   * Creates a Http from a pure function
   */
  def fromFunction[A]: PartialFromFunction[A] = new PartialFromFunction[A](())

  /**
   * Creates a Http from an pure function from A to HExit[R,E,B]
   */
  def fromFunctionHExit[A]: PartialFromFunctionHExit[A] = new PartialFromFunctionHExit[A](())

  /**
   * Creates a Http from an effectful pure function
   */
  def fromFunctionZIO[A]: PartialFromFunctionZIO[A] = new PartialFromFunctionZIO[A](())

  /**
   * Creates a Http from HExit[R,E,B]
   */
  def fromHExit[R, E, B](h: HExit[R, E, B]): Http[R, E, Any, B] = FromHExit(h)

  /**
   * Lifts an `Option` into a `Http` value.
   */
  def fromOption[A](v: Option[A]): Http[Any, Option[Nothing], Any, A] =
    v.fold[Http[Any, Option[Nothing], Any, A]](Http.fail(None))(Http.succeed)

  /**
   * Creates an `Http` from a function that takes a value of type `A` and
   * returns with a `ZIO[R, Option[E], B]`. The returned effect can fail with a
   * `None` to signal "not found" to the backend.
   */
  def fromOptionFunction[A]: PartialFromOptionFunction[A] = new PartialFromOptionFunction(())

  /**
   * Creates an HTTP that can serve files on the give path.
   */
  def fromPath(head: String, tail: String*): HttpApp[Any, Throwable] =
    Http.fromFile(Paths.get(head, tail: _*).toFile)

  /**
   * Creates an Http app from a resource path
   */
  def fromResource(path: String): HttpApp[Blocking, Throwable] =
    Http.getResource(path).flatMap(url => Http.fromFile(new File(url.getPath)))

  /**
   * Creates a Http that always succeeds with a 200 status code and the provided
   * ZStream as the body
   */
  def fromStream[R](stream: ZStream[R, Throwable, String], charset: Charset = HTTP_CHARSET): HttpApp[R, Nothing] =
    Http.fromZIO(ZIO.environment[R].map(r => Http.fromData(HttpData.fromStream(stream.provide(r), charset)))).flatten

  /**
   * Creates a Http that always succeeds with a 200 status code and the provided
   * ZStream as the body
   */
  def fromStream[R](stream: ZStream[R, Throwable, Byte]): HttpApp[R, Nothing] =
    Http.fromZIO(ZIO.environment[R].map(r => Http.fromData(HttpData.fromStream(stream.provide(r))))).flatten

  /**
   * Converts a ZIO to an Http type
   */
  def fromZIO[R, E, B](effect: ZIO[R, E, B]): Http[R, E, Any, B] = Http.fromFunctionZIO(_ => effect)

  /**
   * Attempts to retrieve files from the classpath.
   */
  def getResource(path: String): Http[Blocking, Throwable, Any, net.URL] =
    Http
      .fromZIO(effectBlocking(getClass.getClassLoader.getResource(path)))
      .flatMap { resource => if (resource == null) Http.empty else Http.succeed(resource) }

  /**
   * Attempts to retrieve files from the classpath.
   */
  def getResourceAsFile(path: String): Http[Blocking, Throwable, Any, File] =
    Http.getResource(path).map(url => new File(url.getPath))

  /**
   * Creates an HTTP app which always responds with the provided Html page.
   */
  def html(view: Html): HttpApp[Any, Nothing] = Http.response(Response.html(view))

  /**
   * Creates a pass thru Http instance
   */
  def identity[A]: Http[Any, Nothing, A, A] = Http.Identity

  /**
   * Creates an HTTP app which always responds with a 405 status code.
   */
  def methodNotAllowed(msg: String): HttpApp[Any, Nothing] = Http.error(HttpError.MethodNotAllowed(msg))

  /**
   * Creates an Http app that fails with a NotFound exception.
   */
  def notFound: HttpApp[Any, Nothing] =
    Http.fromFunction[Request](req => Http.error(HttpError.NotFound(req.url.path))).flatten

  /**
   * Creates an HTTP app which always responds with a 200 status code.
   */
  def ok: HttpApp[Any, Nothing] = status(Status.Ok)

  /**
   * Provides access to the request's remote address
   */
  def remoteAddress: Http[Any, IOException, Request, InetAddress] =
    context flatMap { ctx =>
      ctx.channel().remoteAddress() match {
        case m: InetSocketAddress => Http.succeed(m.getAddress)
        case _                    => Http.fail(new IOException("Unable to get remote address"))
      }
    }

  /**
   * Creates an Http app which always responds with the same value.
   */
  def response(response: Response): Http[Any, Nothing, Any, Response] = Http.succeed(response)

  /**
   * Converts a ZIO to an Http app type
   */
  def responseZIO[R, E](res: ZIO[R, E, Response]): HttpApp[R, E] = Http.fromZIO(res)

  /**
   * Creates an Http that delegates to other Https.
   */
  def route[A]: Http.PartialRoute[A] = Http.PartialRoute(())

  /**
   * Creates an HTTP app which always responds with the same status code and
   * empty data.
   */
  def status(code: Status): HttpApp[Any, Nothing] = Http.succeed(Response(code))

  /**
   * Creates an Http that always returns the same response and never fails.
   */
  def succeed[B](b: B): Http[Any, Nothing, Any, B] = Http.Succeed(b)

  /**
   * Creates an Http app which responds with an Html page using the built-in
   * template.
   */
  def template(heading: CharSequence)(view: Html): HttpApp[Any, Nothing] =
    Http.response(Response.html(Template.container(heading)(view)))

  /**
   * Creates an Http app which always responds with the same plain text.
   */
  def text(charSeq: CharSequence): HttpApp[Any, Nothing] =
    Http.succeed(Response.text(charSeq))

  /**
   * Creates an Http app that responds with a 408 status code after the provided
   * time duration
   */
  def timeout(duration: Duration): HttpApp[Clock, Nothing] = Http.status(Status.RequestTimeout).delay(duration)

  /**
   * Creates an HTTP app which always responds with a 413 status code.
   */
  def tooLarge: HttpApp[Any, Nothing] = Http.status(Status.RequestEntityTooLarge)

  /**
   * Provides low level access to an HttpApp to perform unsafe operations using
   * the request's ChannelHandlerContext.
   */
  def usingContext[R, E](f: ChannelHandlerContext => HttpApp[R, E]): HttpApp[R, E] = context.flatMap(f(_))

  // Ctor Help
  final case class PartialCollectZIO[A](unit: Unit) extends AnyVal {
    def apply[R, E, B](pf: PartialFunction[A, ZIO[R, E, B]]): Http[R, E, A, B] =
      Http.collect[A] { case a if pf.isDefinedAt(a) => Http.fromZIO(pf(a)) }.flatten
  }

  final case class PartialCollectManaged[A](unit: Unit) extends AnyVal {
    def apply[R, E, B](pf: PartialFunction[A, ZManaged[R, E, B]]): Http[R, E, A, B] =
      Http.collect[A] { case a if pf.isDefinedAt(a) => Http.fromZIO(pf(a).useNow) }.flatten
  }

  final case class PartialCollect[A](unit: Unit) extends AnyVal {
    def apply[B](pf: PartialFunction[A, B]): Http[Any, Nothing, A, B] = {
      FromFunctionHExit(pf.lift(_) match {
        case Some(value) => HExit.succeed(value)
        case None        => HExit.Empty
      })
    }
  }

  final case class PartialCollectHttp[A](unit: Unit) extends AnyVal {
    def apply[R, E, B](pf: PartialFunction[A, Http[R, E, A, B]]): Http[R, E, A, B] =
      Http.collect[A](pf).flatten
  }

  final case class PartialCollectHExit[A](unit: Unit) extends AnyVal {
    def apply[R, E, B](pf: PartialFunction[A, HExit[R, E, B]]): Http[R, E, A, B] =
      FromFunctionHExit(a => if (pf.isDefinedAt(a)) pf(a) else HExit.empty)
  }

  final case class PartialRoute[A](unit: Unit) extends AnyVal {
    def apply[R, E, B](pf: PartialFunction[A, Http[R, E, A, B]]): Http[R, E, A, B] =
      Http.collect[A] { case r if pf.isDefinedAt(r) => pf(r) }.flatten
  }

  final case class PartialContraFlatMap[-R, +E, -A, +B, X](self: Http[R, E, A, B]) extends AnyVal {
    def apply[R1 <: R, E1 >: E](xa: X => Http[R1, E1, Any, A]): Http[R1, E1, X, B] =
      Http.identity[X].flatMap(xa) >>> self
  }

  final class PartialFromOptionFunction[A](val unit: Unit) extends AnyVal {
    def apply[R, E, B](f: A => ZIO[R, Option[E], B]): Http[R, E, A, B] = Http
      .collectZIO[A] { case a =>
        f(a)
          .map(Http.succeed)
          .catchAll {
            case Some(error) => UIO(Http.fail(error))
            case None        => UIO(Http.empty)
          }
          .catchAllDefect(defect => UIO(Http.die(defect)))
      }
      .flatten
  }

  final class PartialFromFunction[A](val unit: Unit) extends AnyVal {
    def apply[B](f: A => B): Http[Any, Nothing, A, B] = Http.identity[A].map(f)
  }

  final class PartialFromFunctionZIO[A](val unit: Unit) extends AnyVal {
    def apply[R, E, B](f: A => ZIO[R, E, B]): Http[R, E, A, B] = FromFunctionHExit(a => HExit.fromZIO(f(a)))
  }

  final class PartialFromFunctionHExit[A](val unit: Unit) extends AnyVal {
    def apply[R, E, B](f: A => HExit[R, E, B]): Http[R, E, A, B] = FromFunctionHExit(f)
  }

  private final case class Succeed[B](b: B) extends Http[Any, Nothing, Any, B]

  private final case class Race[R, E, A, B](self: Http[R, E, A, B], other: Http[R, E, A, B]) extends Http[R, E, A, B]

  private final case class Fail[E](e: E) extends Http[Any, E, Any, Nothing]

  private final case class Die(t: Throwable) extends Http[Any, Nothing, Any, Nothing]

  private final case class FromFunctionHExit[R, E, A, B](f: A => HExit[R, E, B]) extends Http[R, E, A, B]

  private final case class Chain[R, E, A, B, C](self: Http[R, E, A, B], other: Http[R, E, B, C])
      extends Http[R, E, A, C]

  private final case class FoldHttp[R, E, EE, A, B, BB](
    self: Http[R, E, A, B],
    failure: E => Http[R, EE, A, BB],
    defect: Throwable => Http[R, EE, A, BB],
    success: B => Http[R, EE, A, BB],
    empty: Http[R, EE, A, BB],
  ) extends Http[R, EE, A, BB]

  private final case class RunMiddleware[R, E, A1, B1, A2, B2](
    http: Http[R, E, A1, B1],
    mid: Middleware[R, E, A1, B1, A2, B2],
  ) extends Http[R, E, A2, B2]

  private case class Attempt[A](a: () => A) extends Http[Any, Nothing, Any, A]

  private final case class Combine[R, E, EE, A, B, BB](
    self: Http[R, E, A, B],
    other: Http[R, EE, A, BB],
  ) extends Http[R, EE, A, BB]

  private final case class FromHExit[R, E, B](h: HExit[R, E, B]) extends Http[R, E, Any, B]

  private final case class When[R, E, A, B](f: A => Boolean, other: Http[R, E, A, B]) extends Http[R, E, A, B]

  private case object Empty extends Http[Any, Nothing, Any, Nothing]

  private case object Identity extends Http[Any, Nothing, Any, Nothing]
}
