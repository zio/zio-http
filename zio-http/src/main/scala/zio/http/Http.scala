package zio.http

import io.netty.handler.codec.http.HttpHeaderNames
import zio.ZIO.attemptBlocking
import zio.http.html._
import zio.http.middleware.IT
import zio.http.model._
import zio.http.model.headers.HeaderModifierZIO
import zio.http.socket.{SocketApp, WebSocketChannelEvent}
import zio.stream.ZStream
import zio.{http, _}

import java.io.{File, FileNotFoundException}
import java.net
import java.nio.charset.Charset
import java.nio.file.Paths
import java.util.zip.ZipFile
import scala.annotation.{nowarn, unused}
import scala.reflect.ClassTag
import scala.runtime.AbstractPartialFunction
import scala.util.control.NonFatal
//import zio.stacktracer.TracingImplicits.disableAutoTrace // scalafix:ok; // TODO

/**
 * A functional domain to model Http apps using ZIO and that can work over any
 * kind of request and response types.
 */
sealed trait Http[-R, +E, -A, +B] extends AbstractPartialFunction[A, HExit[R, E, B]] { self =>

  import Http._

  def @@[R1 <: R, E1 >: E, A1 <: A, B1 >: B, A2, B2, A1T <: IT[A1]](
    mid: Middleware[R1, E1, A1, B1, A2, B2, A1T],
  )(implicit trace: Trace, ev: IT.CanApplyToPartial.Aux[A1, A2, A1T]): Http[R1, E1, A2, B2] =
    self.middleware(mid)

  /**
   * Attaches the provided middleware to the Http app TODO; Rename to @@ once
   * http.Middleware is removed
   */
  def withMiddleware[R1 <: R, A1 <: A, I, O](
    mid: api.Middleware[R1, I, O],
  )(implicit trace: Trace, ev1: A1 <:< Request, ev2: B <:< http.Response): HttpApp[R1, E] =
    mid(self.asInstanceOf[Http[R, E, Request, Response]])

  /**
   * Alias for flatmap
   */
  def >>=[R1 <: R, E1 >: E, A1 <: A, C1](f: B => Http.Total[R1, E1, A1, C1]): Http[R1, E1, A1, C1] =
    self.flatMap(f)

  /**
   * Pipes the output of one app into the other
   */
  def >>>[R1 <: R, E1 >: E, B1 >: B, C](other: Http.Total[R1, E1, B1, C]): Http[R1, E1, A, C] =
    self andThen other

  /**
   * Runs self but if it fails, runs other, ignoring the result from self.
   */
  def <>[R1 <: R, E1 >: E, A1 <: A, B1 >: B](other: Http[R1, E1, A1, B1]): Http[R1, E1, A1, B1] =
    self orElse other

  /**
   * Combines two Http into one.
   */
  def ++[R1 <: R, E1 >: E, A1 <: A, B1 >: B](other: Http[R1, E1, A1, B1]): Http[R1, E1, A1, B1] =
    self defaultWith other

  /**
   * Alias for zipRight
   */
  def *>[R1 <: R, E1 >: E, A1 <: A, C1](other: Http.Total[R1, E1, A1, C1]): Http[R1, E1, A1, C1] =
    self.zipRight(other)

  /**
   * Returns an http app that submerges the error case of an `Either` into the
   * `Http`. The inverse operation of `Http.either`.
   */
  def absolve[E1 >: E, C](implicit ev: B <:< Either[E1, C]): Http[R, E1, A, C] =
    self.flatMap(b =>
      ev(b) match {
        case Right(c) => Http.succeed(c)
        case Left(e)  => Http.fail(e)
      },
    )

  /**
   * Named alias for `>>>`
   */
  def andThen[R1 <: R, E1 >: E, B1 >: B, C](other: Http.Total[R1, E1, B1, C]): Http[R1, E1, A, C] =
    Http.Chain(self, other)

  /**
   * Makes the app resolve with a constant value
   */
  def as[C](c: C): Http[R, E, A, C] =
    self *> Http.succeed(c)

  /**
   * Extracts body
   */
  def body(implicit eb: B <:< Response): Http[R, E, A, Body] =
    self.map(_.body)

  /**
   * Catches all the exceptions that the http app can fail with
   */
  def catchAll[R1 <: R, E1, A1 <: A, B1 >: B](f: E => Http.Total[R1, E1, A1, B1])(implicit
    @unused ev: CanFail[E],
  ): Http[R1, E1, A1, B1] =
    self.foldHttp(f, Http.succeed)

  def catchAllCause[R1 <: R, E1, A1 <: A, B1 >: B](
    f: Cause[E] => Http.Total[R1, E1, A1, B1],
  ): Http[R1, E1, A1, B1] =
    self.foldCauseHttp(f, Http.succeed)

  /**
   * Recovers from all defects with provided function.
   *
   * '''WARNING''': There is no sensible way to recover from defects. This
   * method should be used only at the boundary between `Http` and an external
   * system, to transmit information on a defect for diagnostic or explanatory
   * purposes.
   */
  def catchAllDefect[R2 <: R, E2 >: E, A2 <: A, B2 >: B](
    h: Throwable => Http.Total[R2, E2, A2, B2],
  ): Http[R2, E2, A2, B2] =
    self.catchSomeDefect { case t => h(t) }

  /**
   * Recovers from all NonFatal Throwables.
   */
  def catchNonFatalOrDie[R2 <: R, E2 >: E, A2 <: A, B2 >: B](
    h: E => Http.Total[R2, E2, A2, B2],
  )(implicit ev1: CanFail[E], ev2: E <:< Throwable): Http[R2, E2, A2, B2] =
    self.catchSome {
      case e @ NonFatal(_) => h(e)
      case e               => Http.die(e)
    }

  /**
   * Recovers from some or all of the error cases.
   */
  def catchSome[R1 <: R, E1 >: E, A1 <: A, B1 >: B](f: PartialFunction[E, Http.Total[R1, E1, A1, B1]])(implicit
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
  def catchSomeDefect[R1 <: R, E1 >: E, A1 <: A, B1 >: B](
    pf: PartialFunction[Throwable, Http.Total[R1, E1, A1, B1]],
  ): Http[R1, E1, A1, B1] =
    unrefineWith(pf)(Http.fail).catchAll(e => e)

  /**
   * Extracts content-length from the response if available
   */
  def contentLength(implicit eb: B <:< Response): Http[R, E, A, Option[Long]] =
    headers.map(_.contentLength)

  /**
   * Extracts the value of ContentType header
   */
  def contentType(implicit eb: B <:< Response): Http[R, E, A, Option[CharSequence]] =
    headerValue(HttpHeaderNames.CONTENT_TYPE)

  /**
   * Transforms the input of the http before passing it on to the current Http
   */
  def contramap[X](xa: X => A): Http[R, E, X, B] =
    Http.collectHExit {
      val pxa: PartialFunction[X, A] = { case x => xa(x) }
      pxa.andThen(self)
    }

  /**
   * Named alias for `++`
   */
  def defaultWith[R1 <: R, E1 >: E, A1 <: A, B1 >: B](other: Http[R1, E1, A1, B1]): Http[R1, E1, A1, B1] =
    Http.Combine(self, other)

  /**
   * Delays production of output B for the specified duration of time
   */
  def delay(duration: Duration)(implicit trace: Trace): Http[R, E, A, B] = self.delayAfter(duration)

  /**
   * Delays production of output B for the specified duration of time
   */
  def delayAfter(duration: Duration)(implicit trace: Trace): Http[R, E, A, B] =
    self.mapZIO(b => ZIO.succeed(b).delay(duration))

  /**
   * Returns an http app whose failure and success have been lifted into an
   * `Either`. The resulting app cannot fail, because the failure case has been
   * exposed as part of the `Either` success case.
   */
  def either(implicit ev: CanFail[E]): Http[R, Nothing, A, Either[E, B]] =
    self.foldHttp(
      e => Http.succeed(Left(e)),
      b => Http.succeed(Right(b)),
    )

  /**
   * Creates a new Http app from another
   */
  def flatMap[R1 <: R, E1 >: E, A1 <: A, C1](f: B => Http.Total[R1, E1, A1, C1]): Http[R1, E1, A1, C1] = {
    self.foldHttp(Http.fail, f)
  }

  /**
   * Flattens an Http app of an Http app
   */
  def flatten[R1 <: R, E1 >: E, A1 <: A, B1](implicit
    ev: B <:< Http.Total[R1, E1, A1, B1],
  ): Http[R1, E1, A1, B1] = {
    self.flatMap(scala.Predef.identity(_))
  }

  def foldCauseHttp[R1 <: R, E1, A1 <: A, C1](
    failure: Cause[E] => Http.Total[R1, E1, A1, C1],
    success: B => Http.Total[R1, E1, A1, C1],
  ): Http[R1, E1, A1, C1] =
    Http.FoldHttp(self, failure, success)

  /**
   * Folds over the http app by taking in two functions one for success and one
   * for failure respectively.
   */
  def foldHttp[R1 <: R, A1 <: A, E1, B1](
    failure: E => Http.Total[R1, E1, A1, B1],
    success: B => Http.Total[R1, E1, A1, B1],
  ): Http[R1, E1, A1, B1] =
    foldCauseHttp(c => c.failureOrCause.fold(failure, Http.failCause(_)), success)

  /**
   * Extracts the value of the provided header name.
   */
  def headerValue(name: CharSequence)(implicit eb: B <:< Response): Http[R, E, A, Option[CharSequence]] =
    headers.map(_.headerValue(name))

  /**
   * Extracts the `Headers` from the type `B` if possible
   */
  def headers(implicit eb: B <:< Response): Http[R, E, A, Headers] = self.map(_.headers)

  /**
   * Transforms the output of the http app
   */
  def map[C](bc: B => C): Http[R, E, A, C] = self.flatMap(b => Http.succeed(bc(b)))

  /**
   * Transforms the failure of the http app
   */
  def mapError[E1](ee: E => E1): Http[R, E1, A, B] =
    self.foldHttp(e => Http.fail(ee(e)), Http.succeed)

  /**
   * Transforms the output of the http effectfully
   */
  def mapZIO[R1 <: R, E1 >: E, C](bFc: B => ZIO[R1, E1, C])(implicit trace: Trace): Http[R1, E1, A, C] =
    self >>> Http.fromFunctionZIO(bFc)

  /**
   * Returns a new Http where the error channel has been merged into the success
   * channel to their common combined type.
   */
  def merge[E1 >: E, B1 >: B](implicit ev: E1 =:= B1): Http[R, Nothing, A, B1] =
    self.catchAll(Http.succeed(_))

  /**
   * Named alias for @@
   */
  def middleware[R1 <: R, E1 >: E, A1 <: A, B1 >: B, A2, B2, A1T <: IT[A1]](
    mid: Middleware[R1, E1, A1, B1, A2, B2, A1T],
  )(implicit canApplyToPartial: IT.CanApplyToPartial.Aux[A1, A2, A1T]): Http[R1, E1, A2, B2] =
    ApplyMiddleware(self, mid, canApplyToPartial)

  /**
   * Narrows the type of the input
   */
  def narrow[A1](implicit a: A1 <:< A): Http[R, E, A1, B] = self.asInstanceOf[Http[R, E, A1, B]]

  /**
   * Executes this app, skipping the error but returning optionally the success.
   */
  def option(implicit ev: CanFail[E]): Http[R, Nothing, A, Option[B]] =
    self.foldHttp(
      _ => Http.succeed(None),
      b => Http.succeed(Some(b)),
    )

  /**
   * Converts an option on errors into an option on values.
   */
  def optional[E1](implicit ev: E <:< Option[E1]): Http[R, E1, A, Option[B]] =
    self.foldHttp(
      ev(_) match {
        case Some(e) => Http.fail(e)
        case None    => Http.succeed(None)
      },
      b => Http.succeed(Some(b)),
    )

  /**
   * Translates app failure into death of the app, making all failures unchecked
   * and not a part of the type of the app.
   */
  def orDie(implicit ev1: E <:< Throwable, ev2: CanFail[E]): Http[R, Nothing, A, B] =
    orDieWith(ev1)

  /**
   * Keeps none of the errors, and terminates the http app with them, using the
   * specified function to convert the `E` into a `Throwable`.
   */
  def orDieWith(f: E => Throwable)(implicit ev: CanFail[E]): Http[R, Nothing, A, B] =
    self.foldHttp(e => Http.die(f(e)), Http.succeed)

  /**
   * Named alias for `<>`
   */
  def orElse[R1 <: R, E1 >: E, A1 <: A, B1 >: B](other: Http[R1, E1, A1, B1]): Http[R1, E1, A1, B1] =
    OrElse(self, other)

  /**
   * Provides the environment to Http.
   */
  def provideEnvironment(r: ZEnvironment[R]): Http[Any, E, A, B] =
    Aspect(self, (z: ZIO[R, E, B]) => z.provideEnvironment(r))

  /**
   * Provides layer to Http.
   */
  def provideLayer[E1 >: E, R0](
    layer: ZLayer[R0, E1, R],
  )(implicit trace: Trace): Http[R0, E1, A, B] =
    Aspect(self, (z: ZIO[R, E1, B]) => z.provideLayer(layer))

  /**
   * Provides some of the environment to Http.
   */
  def provideSomeEnvironment[R1](
    r: ZEnvironment[R1] => ZEnvironment[R],
  )(implicit trace: Trace): Http[R1, E, A, B] =
    Aspect(self, (z: ZIO[R, E, B]) => z.provideSomeEnvironment[R1](r))

  /**
   * Provides some of the environment to Http leaving the remainder `R0`.
   */
  def provideSomeLayer[R0, R1, E1 >: E](
    layer: ZLayer[R0, E1, R1],
  )(implicit ev: R0 with R1 <:< R, tagged: Tag[R1], trace: Trace): Http[R0, E1, A, B] =
    Aspect(self, (z: ZIO[R, E1, B]) => z.provideSomeLayer[R0](layer))

  /**
   * Performs a race between two apps
   */
  def race[R1 <: R, E1 >: E, A1 <: A, B1 >: B](other: Http[R1, E1, A1, B1]): Http[R1, E1, A1, B1] =
    Race(self, other)

  /**
   * Keeps some of the errors, and terminates the http app with the rest.
   */
  def refineOrDie[E1](
    pf: PartialFunction[E, E1],
  )(implicit ev1: E <:< Throwable, ev2: CanFail[E]): Http[R, E1, A, B] =
    refineOrDieWith(pf)(ev1)

  /**
   * Keeps some of the errors, and terminates the http app with the rest, using
   * the specified function to convert the `E` into a `Throwable`.
   */
  def refineOrDieWith[E1](pf: PartialFunction[E, E1])(f: E => Throwable)(implicit
    ev: CanFail[E],
  ): Http[R, E1, A, B] =
    self.catchAll(err => (pf lift err).fold[Http.Total[R, E1, A, B]](Http.die(f(err)))(Http.fail))

  /**
   * Extracts `Status` from the type `B` is possible.
   */
  def status(implicit ev: B <:< Response): Http[R, E, A, Status] = self.map(_.status)

  /**
   * Returns an Http that effectfully peeks at the success, failed, defective or
   * empty value of this Http.
   */
  def tapAllZIO[R1 <: R, E1 >: E](
    failure: Cause[E] => ZIO[R1, E1, Any],
    success: B => ZIO[R1, E1, Any],
  ): Http[R1, E1, A, B] =
    Aspect(self, (z: ZIO[R, E, B]) => z.tapErrorCause(failure).tap(success))

  /**
   * Returns an Http that effectfully peeks at the failure of this Http.
   */
  def tapErrorCauseZIO[R1 <: R, E1 >: E](f: Cause[E] => ZIO[R1, E1, Any]): Http[R1, E1, A, B] =
    self.tapAllZIO(f, _ => ZIO.unit)

  /**
   * Returns an Http that effectfully peeks at the failure of this Http.
   */
  def tapErrorZIO[R1 <: R, E1 >: E](f: E => ZIO[R1, E1, Any]): Http[R1, E1, A, B] =
    self.tapErrorCauseZIO(cause => cause.failureOption.fold[ZIO[R1, E1, Any]](ZIO.unit)(f))

  /**
   * Returns an Http that effectfully peeks at the success of this Http.
   */
  def tapZIO[R1 <: R, E1 >: E](f: B => ZIO[R1, E1, Any]): Http[R1, E1, A, B] =
    self.tapAllZIO(_ => ZIO.unit, f)

  /**
   * Converts an Http into a websocket application
   */
  def toSocketApp(implicit a: WebSocketChannelEvent <:< A, e: E <:< Throwable, trace: Trace): SocketApp[R] =
    SocketApp(event =>
      self.toZIO(event).catchAll {
        case Some(value) => ZIO.fail(value)
        case None        => ZIO.unit
      },
    )

  /**
   * Takes some defects and converts them into failures.
   */
  def unrefine[E1 >: E](pf: PartialFunction[Throwable, E1]): Http[R, E1, A, B] =
    unrefineWith(pf)(e => e)

  /**
   * Takes some defects and converts them into failures.
   */
  def unrefineTo[E1 >: E: ClassTag]: Http[R, E1, A, B] =
    unrefine { case e: E1 => e }

  /**
   * Takes some defects and converts them into failures, using the specified
   * function to convert the `E` into an `E1`.
   */
  def unrefineWith[E1](pf: PartialFunction[Throwable, E1])(f: E => E1): Http[R, E1, A, B] =
    catchAllCause { cause =>
      cause.find {
        case Cause.Die(t, _) if pf.isDefinedAt(t) => pf(t)
      }.fold(Http.failCause(cause.map(f)))(Http.fail(_))
    }

  /**
   * Unwraps an Http that returns a ZIO of Http
   */
  def unwrap[R1 <: R, E1 >: E, C](implicit ev: B <:< ZIO[R1, E1, C], trace: Trace): Http[R1, E1, A, C] =
    self.flatMap(Http.fromZIO(_))

  /**
   * Applies Http based only if the condition function evaluates to true
   */
  def when[A2 <: A](f: A2 => Boolean): Http[R, E, A2, B] =
    Http.When(f, self)

  /**
   * Widens the type of the output
   */
  def widen[E1, B1](implicit e: E <:< E1, b: B <:< B1): Http[R, E1, A, B1] =
    self.asInstanceOf[Http[R, E1, A, B1]]

  def withFallback[R1 <: R, E1 >: E, A1 <: A, B1 >: B](other: Http.Total[R1, E1, A1, B1]): Http.Total[R1, E1, A1, B1] =
    CombineTotal(self, other)

  def wrap[R2, E2, B2, AA <: A](f: (AA, ZIO[R, E, B]) => ZIO[R2, E2, B2]): Http[R2, E2, AA, B2] =
    Http.collectZIO {
      case a if self.isDefinedAt(a) =>
        f(a, self.executeToZIO(a))
    }

  /**
   * Combines the two apps and returns the result of the one on the right
   */
  def zipRight[R1 <: R, E1 >: E, A1 <: A, C1](other: Http.Total[R1, E1, A1, C1]): Http[R1, E1, A1, C1] =
    self.flatMap(_ => other)

  final private[zio] def executeToZIO(a: A): ZIO[R, E, B] =
    toHExit(a).toZIO

  final private[zio] def toHExit(a: A): HExit[R, E, B] = {
    val hExit = toHExitOrNull(a)
    if (hExit ne null) hExit
    else HExit.die(new MatchError(a))
  }

  private[zio] def toHExitOrNull(a: A): HExit[R, E, B] =
    self.applyOrElse(a, (_: A) => null)

  /**
   * Consumes the input and executes the Http.
   */
  def toZIO(a: A)(implicit trace: Trace): ZIO[R, Option[E], B] = {
    val hExit = toHExitOrNull(a)
    if (hExit ne null) hExit.toZIO.mapError(Some(_))
    else ZIO.fail(None)
  }
}

object Http {

  implicit final class HttpAppSyntax[-R, +E](val http: HttpApp[R, E]) extends HeaderModifierZIO[HttpApp[R, E]] {
    self =>

    /**
     * Patches the response produced by the app
     */
    def patch(patch: Patch): HttpApp[R, E] = http.map(patch(_))

    /**
     * Overwrites the method in the incoming request
     */
    def setMethod(method: Method): HttpApp[R, E] = http.contramap[Request](_.copy(method = method))

    /**
     * Overwrites the path in the incoming request
     */
    def setPath(path: Path): HttpApp[R, E] = http.contramap[Request](_.updatePath(path))

    /**
     * Sets the status in the response produced by the app
     */
    def setStatus(status: Status): HttpApp[R, E] = patch(Patch.setStatus(status))

    /**
     * Overwrites the url in the incoming request
     */
    def setUrl(url: URL): HttpApp[R, E] = http.contramap[Request](_.copy(url = url))

    /**
     * Updates the response headers using the provided function
     */
    override def updateHeaders(update: Headers => Headers)(implicit trace: Trace): HttpApp[R, E] =
      http.map(_.updateHeaders(update))

    /**
     * Applies Http based on the path
     */
    def whenPathEq(p: Path): HttpApp[R, E] =
      http.whenPathEq(p.encode)

    /**
     * Applies Http based on the path as string
     */
    def whenPathEq(p: String): HttpApp[R, E] = {
      http.when { a =>
        a.url.path.encode.contentEquals(p)
      }
    }
  }

  implicit final class TotalHttpAppSyntax[-R, +E](val http: Http.Total[R, E, Request, Response])
      extends HeaderModifierZIO[Http.Total[R, E, Request, Response]] {
    self =>

    /**
     * Patches the response produced by the app
     */
    def patch(patch: Patch): Http.Total[R, E, Request, Response] = http.map(patch(_))

    /**
     * Overwrites the method in the incoming request
     */
    def setMethod(method: Method): Http.Total[R, E, Request, Response] =
      http.contramap[Request](_.copy(method = method))

    /**
     * Overwrites the path in the incoming request
     */
    def setPath(path: Path): Http.Total[R, E, Request, Response] = http.contramap[Request](_.updatePath(path))

    /**
     * Sets the status in the response produced by the app
     */
    def setStatus(status: Status): Http.Total[R, E, Request, Response] = patch(Patch.setStatus(status))

    /**
     * Overwrites the url in the incoming request
     */
    def setUrl(url: URL): Http.Total[R, E, Request, Response] = http.contramap[Request](_.copy(url = url))

    /**
     * Updates the response headers using the provided function
     */
    override def updateHeaders(update: Headers => Headers)(implicit trace: Trace): Http.Total[R, E, Request, Response] =
      http.map(_.updateHeaders(update))

    /**
     * Applies Http based on the path
     */
    def whenPathEq(p: Path): Http[R, E, Request, Response] =
      http.whenPathEq(p.encode)

    /**
     * Applies Http based on the path as string
     */
    def whenPathEq(p: String): Http[R, E, Request, Response] = {
      http.when { a =>
        a.url.path.encode.contentEquals(p)
      }
    }
  }

  /**
   * Equivalent to `Http.succeed`
   */
  def apply[B](b: B): Http.Total[Any, Nothing, Any, B] = Http.succeed(b)

  /**
   * Attempts to create an Http that succeeds with the provided value, capturing
   * all exceptions on it's way.
   */
  def attempt[A](a: => A): Http.Total[Any, Throwable, Any, A] = Attempt(() => a)

  /**
   * Creates an HTTP app which always responds with a 400 status code.
   */
  def badRequest(msg: String): Http.Total[Any, Nothing, Request, Response] = Http.error(HttpError.BadRequest(msg))

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
   * Returns an http app that dies with the specified `Throwable`. This method
   * can be used for terminating an app because a defect has been detected in
   * the code. Terminating an http app leads to aborting handling of an HTTP
   * request and responding with 500 Internal Server Error.
   */
  def die(t: Throwable): Http.Total[Any, Nothing, Any, Nothing] =
    failCause(Cause.die(t))

  /**
   * Returns an app that dies with a `RuntimeException` having the specified
   * text message. This method can be used for terminating a HTTP request
   * because a defect has been detected in the code.
   */
  def dieMessage(message: => String): Http.Total[Any, Nothing, Any, Nothing] =
    die(new RuntimeException(message))

  /**
   * Creates an empty Http value
   */
  def empty: Http[Any, Nothing, Any, Nothing] = Http.Empty

  /**
   * Creates an HTTP app with HttpError.
   */
  def error(cause: HttpError): Http.Total[Any, Nothing, Request, Response] =
    Http.response(Response.fromHttpError(cause))

  /**
   * Creates an Http app that responds with 500 status code
   */
  def error(msg: String): Http.Total[Any, Nothing, Request, Response] = Http.error(HttpError.InternalServerError(msg))

  /**
   * Creates an Http that always fails
   */
  def fail[E](e: E): Http.Total[Any, E, Any, Nothing] =
    failCause(Cause.fail(e))

  def failCause[E](cause: Cause[E]): Http.Total[Any, E, Any, Nothing] =
    Http.Fail(cause)

  /**
   * Flattens an Http app of an Http app
   */
  def flatten[R, E, A, B](http: Http[R, E, A, Http.Total[R, E, A, B]]): Http[R, E, A, B] =
    http.flatten

  /**
   * Flattens an Http app of an that returns an effectful response
   */
  def flattenZIO[R, E, A, B](http: Http[R, E, A, ZIO[R, E, B]])(implicit trace: Trace): Http[R, E, A, B] =
    http.flatMap(Http.fromZIO)

  /**
   * Creates an Http app that responds with 403 - Forbidden status code
   */
  def forbidden(msg: String): Http.Total[Any, Nothing, Request, Response] = Http.error(HttpError.Forbidden(msg))

  /**
   * Creates an Http app which always responds the provided data and a 200
   * status code
   */
  def fromBody(body: Body): Http.Total[Any, Nothing, Request, Response] = response(Response(body = body))

  /**
   * Lifts an `Either` into a `Http` value.
   */
  def fromEither[E, A](v: Either[E, A]): Http.Total[Any, E, Any, A] =
    v.fold(Http.fail, Http.succeed)

  /**
   * Creates an Http app from the contents of a file.
   */
  def fromFile(file: => java.io.File)(implicit trace: Trace): Http.Total[Any, Throwable, Request, Response] =
    Http.fromFileZIO(ZIO.succeed(file))

  /**
   * Creates an Http app from the contents of a file which is produced from an
   * effect. The operator automatically adds the content-length and content-type
   * headers if possible.
   */
  def fromFileZIO[R](
    fileZIO: ZIO[R, Throwable, java.io.File],
  )(implicit trace: Trace): Http.Total[R, Throwable, Request, Response] = {
    val response: ZIO[R, Throwable, Http.Total[R, Throwable, Request, Response]] =
      fileZIO.flatMap { file =>
        ZIO.attempt {
          if (file.isFile) {
            val length   = Headers.contentLength(file.length())
            val response = http.Response(headers = length, body = Body.fromFile(file))
            val pathName = file.toPath.toString

            // Set MIME type in the response headers. This is only relevant in
            // case of RandomAccessFile transfers as browsers use the MIME type,
            // not the file extension, to determine how to process a URL.
            // {{{<a href="MSDN Doc">https://developer.mozilla.org/en-US/docs/Web/HTTP/Headers/Content-Type</a>}}}
            Http.succeed(determineMediaType(pathName).fold(response)(response.withMediaType))
          } else Http.fail(new IllegalArgumentException(s"File $file is not a file"))
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
  def fromHExit[R, E, B](h: HExit[R, E, B]): Http.Total[R, E, Any, B] = FromHExit(h)

  /**
   * Lifts an `Option` into a `Http` value.
   */
  def fromOption[A](v: Option[A]): Http[Any, Option[Nothing], Any, A] =
    v.fold[Http[Any, Option[Nothing], Any, A]](Http.fail(None))(Http.succeed)

  /**
   * Creates an HTTP that can serve files on the give path.
   */
  def fromPath(head: String, tail: String*)(implicit trace: Trace): Http.Total[Any, Throwable, Request, Response] =
    Http.fromFile(Paths.get(head, tail: _*).toFile)

  /**
   * Creates an Http app from a resource path
   */
  def fromResource(path: String)(implicit trace: Trace): Http.Total[Any, Throwable, Request, Response] =
    Http.getResource(path).flatMap(url => Http.fromResourceWithURL(url))

  private[zio] def fromResourceWithURL(
    url: java.net.URL,
  )(implicit trace: Trace): Http.Total[Any, Throwable, Request, Response] = {
    url.getProtocol match {
      case "file" =>
        Http.fromFile(new File(url.getPath))
      case "jar"  =>
        val path         = new java.net.URI(url.getPath).getPath // remove "file:" prefix and normalize whitespace
        val bangIndex    = path.indexOf('!')
        val filePath     = path.substring(0, bangIndex)
        val resourcePath = path.substring(bangIndex + 2)
        val mediaType    = determineMediaType(resourcePath)
        val openZip      = ZIO.attemptBlockingIO(new ZipFile(filePath))
        val closeZip     = (jar: ZipFile) => ZIO.attemptBlocking(jar.close()).ignoreLogged
        def fileNotFound = new FileNotFoundException(s"Resource $resourcePath not found")
        def isDirectory  = new IllegalArgumentException(s"Resource $resourcePath is a directory")

        val appZIO =
          ZIO.acquireReleaseWith(openZip)(closeZip) { jar =>
            for {
              entry <- ZIO
                .attemptBlocking(Option(jar.getEntry(resourcePath)))
                .collect(fileNotFound) { case Some(e) => e }
              _     <- ZIO.when(entry.isDirectory)(ZIO.fail(isDirectory))
              contentLength = entry.getSize
              inZStream     = ZStream
                .acquireReleaseWith(openZip)(closeZip)
                .mapZIO(jar => ZIO.attemptBlocking(jar.getEntry(resourcePath) -> jar))
                .flatMap { case (entry, jar) => ZStream.fromInputStream(jar.getInputStream(entry)) }
              response      = Response(body = Body.fromStream(inZStream))
            } yield mediaType.fold(response) { t =>
              response.withMediaType(t).withContentLength(contentLength)
            }
          }

        Http.fromZIO(appZIO)
      case proto  =>
        Http.fail(new IllegalArgumentException(s"Unsupported protocol: $proto"))
    }
  }

  private def determineMediaType(filePath: String): Option[MediaType] = {
    filePath.lastIndexOf(".") match {
      case -1 => None
      case i  =>
        // Extract file extension
        val ext = filePath.substring(i + 1)
        MediaType.forFileExtension(ext)
    }
  }

  /**
   * Creates a Http that always succeeds with a 200 status code and the provided
   * ZStream as the body
   */
  def fromStream[R](stream: ZStream[R, Throwable, String], charset: Charset = HTTP_CHARSET)(implicit
    trace: Trace,
  ): Http.Total[R, Nothing, Request, Response] =
    Http
      .fromZIO(ZIO.environment[R].map(r => Http.fromBody(Body.fromStream(stream.provideEnvironment(r), charset))))
      .flatten

  /**
   * Creates a Http that always succeeds with a 200 status code and the provided
   * ZStream as the body
   */
  def fromStream[R](stream: ZStream[R, Throwable, Byte])(implicit trace: Trace): HttpApp[R, Nothing] =
    Http.fromZIO(ZIO.environment[R].map(r => Http.fromBody(Body.fromStream(stream.provideEnvironment(r))))).flatten

  /**
   * Converts a ZIO to an Http type
   */
  def fromZIO[R, E, B](effect: ZIO[R, E, B])(implicit trace: Trace): Http.Total[R, E, Any, B] =
    Http.fromFunctionZIO(_ => effect)

  /**
   * Attempts to retrieve files from the classpath.
   */
  def getResource(path: String)(implicit trace: Trace): Http.Total[Any, Throwable, Any, net.URL] =
    Http
      .fromZIO(attemptBlocking(getClass.getClassLoader.getResource(path)))
      .flatMap { resource =>
        if (resource == null) Http.fail(new IllegalArgumentException(s"Resource $path not found"))
        else Http.succeed(resource)
      }

  /**
   * Attempts to retrieve files from the classpath.
   */
  def getResourceAsFile(path: String)(implicit trace: Trace): Http.Total[Any, Throwable, Any, File] =
    Http.getResource(path).map(url => new File(url.getPath))

  /**
   * Creates an HTTP app which always responds with the provided Html page.
   */
  def html(view: Html): HttpApp[Any, Nothing] = Http.response(Response.html(view))

  /**
   * Creates a pass thru Http instance
   */
  def identity[A]: Http.Total[Any, Nothing, A, A] = Http.Identity()

  /**
   * Creates an HTTP app which always responds with a 405 status code.
   */
  def methodNotAllowed(msg: String): HttpApp[Any, Nothing] = Http.error(HttpError.MethodNotAllowed(msg))

  /**
   * Creates an Http app that fails with a NotFound exception.
   */
  def notFound: Http.Total[Any, Nothing, Request, Response] =
    Http.fromFunction[Request](req => Http.error(HttpError.NotFound(req.url.path.encode))).flatten

  /**
   * Creates an HTTP app which always responds with a 200 status code.
   */
  def ok: Http.Total[Any, Nothing, Request, Response] = status(Status.Ok)

  /**
   * Creates an Http app which always responds with the same value.
   */
  def response(response: Response): Http.Total[Any, Nothing, Any, Response] = Http.succeed(response)

  /**
   * Converts a ZIO to an Http app type
   */
  def responseZIO[R, E](res: ZIO[R, E, Response])(implicit trace: Trace): Http.Total[R, E, Request, Response] =
    Http.fromZIO(res)

  def stackTrace(implicit trace: Trace): Http.Total[Any, Nothing, Any, StackTrace] =
    Http.fromZIO(ZIO.stackTrace)

  /**
   * Creates an HTTP app which always responds with the same status code and
   * empty data.
   */
  def status(code: Status): Http.Total[Any, Nothing, Request, Response] = Http.succeed(http.Response(code))

  /**
   * Creates an Http that always returns the same response and never fails.
   */
  def succeed[B](b: B): Http.Total[Any, Nothing, Any, B] = Http.Succeed(b)

  /**
   * Creates an Http app which responds with an Html page using the built-in
   * template.
   */
  def template(heading: CharSequence)(view: Html): Http.Total[Any, Nothing, Request, Response] =
    Http.response(Response.html(Template.container(heading)(view)))

  /**
   * Creates an Http app which always responds with the same plain text.
   */
  def text(charSeq: CharSequence): Http.Total[Any, Nothing, Request, Response] =
    Http.succeed(Response.text(charSeq))

  /**
   * Creates an Http app that responds with a 408 status code after the provided
   * time duration
   */
  def timeout(duration: Duration)(implicit trace: Trace): HttpApp[Any, Nothing] =
    Http.status(Status.RequestTimeout).delay(duration)

  /**
   * Creates an HTTP app which always responds with a 413 status code.
   */
  def tooLarge: Http.Total[Any, Nothing, Request, Response] = Http.status(Status.RequestEntityTooLarge)

  // Ctor Help
  final case class PartialCollectZIO[A](unit: Unit) extends AnyVal {
    def apply[R, E, B](pf: PartialFunction[A, ZIO[R, E, B]])(implicit trace: Trace): Http[R, E, A, B] =
      PartialHandler(pf.andThen(b => HExit.fromZIO(b)))
  }

  final case class PartialCollect[A](unit: Unit) extends AnyVal {
    def apply[B](pf: PartialFunction[A, B]): Http[Any, Nothing, A, B] = {
      PartialHandler(pf.andThen(b => HExit.succeed(b)))
    }
  }

  final case class PartialCollectHttp[A](unit: Unit) extends AnyVal {
    def apply[R, E, B](pf: PartialFunction[A, Http[R, E, A, B]]): Http[R, E, A, B] =
      PartialHandler {
        val pf2: PartialFunction[A, (A, Http[R, E, A, B])] = {
          case a if pf.isDefinedAt(a) => (a, pf(a))
        }
        pf2.andThen { case (a, http) if http.isDefinedAt(a) => http(a) }
      }
  }

  final case class PartialCollectHExit[A](unit: Unit) extends AnyVal {
    def apply[R, E, B](pf: PartialFunction[A, HExit[R, E, B]]): Http[R, E, A, B] =
      PartialHandler(pf)
  }

  final case class PartialContraFlatMap[-R, +E, -A, +B, X](self: Http.Total[R, E, A, B]) extends AnyVal {
    def apply[R1 <: R, E1 >: E](xa: X => Http.Total[R1, E1, Any, A]): Http[R1, E1, X, B] =
      Http.collectHttp[X] { case x => xa(x) >>> self }
  }

  final class PartialFromFunction[A](val unit: Unit) extends AnyVal {
    def apply[B](f: A => B): Http.Total[Any, Nothing, A, B] =
      TotalHandler((a: A) => HExit.succeed(f(a)))
  }

  final class PartialFromFunctionZIO[A](val unit: Unit) extends AnyVal {
    def apply[R, E, B](f: A => ZIO[R, E, B])(implicit trace: Trace): Http.Total[R, E, A, B] =
      TotalHandler(a => HExit.fromZIO(f(a)))
  }

  final class PartialFromFunctionHExit[A](val unit: Unit) extends AnyVal {
    def apply[R, E, B](f: A => HExit[R, E, B]): Http.Total[R, E, A, B] =
      TotalHandler(a => f(a))
  }

  // Http constructors

  sealed trait Total[-R, +E, -A, +B] extends Http[R, E, A, B] { self =>
    final override def isDefinedAt(x: A): Boolean                                               = true
    final override def applyOrElse[A1 <: A, B1 >: HExit[R, E, B]](x: A1, default: A1 => B1): B1 = self.apply(x)

    def toZIO(a: A): ZIO[R, E, B] =
      self(a).toZIO

    final private[zio] def executeTotal(a: A): HExit[R, E, B] =
      self(a)

    override private[zio] def toHExitOrNull(a: A): HExit[R, E, B] =
      self(a)

    def @@[R1 <: R, E1 >: E, A1 <: A, B1 >: B, A2, B2, A1T <: IT[A1]](
      mid: Middleware[R1, E1, A1, B1, A2, B2, A1T],
    ): Http.Total[R1, E1, A2, B2] =
      self.middleware(mid)

    /**
     * Pipes the output of one app into the other
     */
    override def >>>[R1 <: R, E1 >: E, B1 >: B, C](other: Http.Total[R1, E1, B1, C]): Http.Total[R1, E1, A, C] =
      self andThen other

    /**
     * Composes one Http app with another.
     */
    def <<<[R1 <: R, E1 >: E, A1 <: A, X](other: Http[R1, E1, X, A1]): Http[R1, E1, X, B] =
      self compose other

    /**
     * Combines two Http instances into a middleware that works a codec for
     * incoming and outgoing messages.
     */
    def \/[R1 <: R, E1 >: E, C, D](other: Http.Total[R1, E1, C, D]): Middleware[R1, E1, B, C, A, D, IT.Impossible[B]] =
      self codecMiddleware other

    /**
     * Alias for zipRight
     */
    override def *>[R1 <: R, E1 >: E, A1 <: A, C1](other: Http.Total[R1, E1, A1, C1]): Http.Total[R1, E1, A1, C1] =
      self.zipRight(other)

    /**
     * Runs self but if it fails, runs other, ignoring the result from self.
     */
    def <>[R1 <: R, E1 >: E, A1 <: A, B1 >: B](other: Http.Total[R1, E1, A1, B1]): Http.Total[R1, E1, A1, B1] =
      self orElse other

    /**
     * Named alias for `>>>`
     */
    override def andThen[R1 <: R, E1 >: E, B1 >: B, C](other: Http.Total[R1, E1, B1, C]): Http.Total[R1, E1, A, C] =
      Http.ChainTotal(self, other)

    /**
     * Combines two Http instances into a middleware that works a codec for
     * incoming and outgoing messages.
     */
    def codecMiddleware[R1 <: R, E1 >: E, C, D](
      other: Http.Total[R1, E1, C, D],
    ): Middleware[R1, E1, B, C, A, D, IT.Impossible[B]] =
      Middleware.codecHttp(self, other)

    /**
     * Named alias for `<<<`
     */
    def compose[R1 <: R, E1 >: E, A1 <: A, C1](other: Http[R1, E1, C1, A1]): Http[R1, E1, C1, B] =
      other andThen self

    /**
     * Transforms the input of the http before passing it on to the current Http
     */
    override def contramap[X](xa: X => A): Http.Total[R, E, X, B] =
      Http.identity[X].map(xa) >>> self

    /**
     * Transforms the input of the http before giving it effectfully
     */
    def contramapZIO[R1 <: R, E1 >: E, X](xa: X => ZIO[R1, E1, A])(implicit
      trace: Trace,
    ): Http.Total[R1, E1, X, B] =
      Http.fromFunctionZIO[X](xa) >>> self

    /**
     * Like `collect` but is applied on the incoming type `A`.
     */
    def contraCollect[X](pf: PartialFunction[X, A]): Http[R, E, X, B] =
      Http.collectHttp[X] {
        case x if pf.isDefinedAt(x) => self.contramap(pf(_))
      }

    /**
     * Transforms the input of the http before passing it on to the current Http
     */
    def contraFlatMap[X]: PartialContraFlatMap[R, E, A, B, X] = PartialContraFlatMap[R, E, A, B, X](self)

    /**
     * Delays consumption of input A for the specified duration of time
     */
    def delayBefore(duration: Duration)(implicit trace: Trace): Http[R, E, A, B] =
      self.contramapZIO(a => ZIO.succeed(a).delay(duration))

    /**
     * Delays production of output B for the specified duration of time
     */
    override def delay(duration: Duration)(implicit trace: Trace): Http.Total[R, E, A, B] =
      self.delayAfter(duration)

    /**
     * Delays production of output B for the specified duration of time
     */
    override def delayAfter(duration: Duration)(implicit trace: Trace): Http.Total[R, E, A, B] =
      self.mapZIO(b => ZIO.succeed(b).delay(duration))

    /**
     * Flattens an Http app of an Http app
     */
    override def flatten[R1 <: R, E1 >: E, A1 <: A, B1](implicit
      ev: B <:< Http.Total[R1, E1, A1, B1],
    ): Http.Total[R1, E1, A1, B1] = {
      self.flatMap(scala.Predef.identity(_))
    }

    /**
     * Creates a new Http app from another
     */
    override def flatMap[R1 <: R, E1 >: E, A1 <: A, C1](
      f: B => Http.Total[R1, E1, A1, C1],
    ): Http.Total[R1, E1, A1, C1] = {
      self.foldHttp(Http.fail, f)
    }

    override def foldCauseHttp[R1 <: R, E1, A1 <: A, C1](
      failure: Cause[E] => Http.Total[R1, E1, A1, C1],
      success: B => Http.Total[R1, E1, A1, C1],
    ): Http.Total[R1, E1, A1, C1] =
      Http.FoldHttpTotal(self, failure, success)

    /**
     * Folds over the http app by taking in two functions one for success and
     * one for failure respectively.
     */
    override def foldHttp[R1 <: R, A1 <: A, E1, B1](
      failure: E => Http.Total[R1, E1, A1, B1],
      success: B => Http.Total[R1, E1, A1, B1],
    ): Http.Total[R1, E1, A1, B1] =
      self.foldCauseHttp(c => c.failureOrCause.fold(failure, Http.failCause(_)), success)

    override def map[C](bc: B => C): Http.Total[R, E, A, C] =
      self.flatMap(b => Http.succeed(bc(b)))

    override def mapZIO[R1 <: R, E1 >: E, C](bFc: B => ZIO[R1, E1, C])(implicit
      trace: Trace,
    ): Http.Total[R1, E1, A, C] =
      self >>> Http.fromFunctionZIO(bFc)

    /**
     * Named alias for @@
     */
    def middleware[R1 <: R, E1 >: E, A1 <: A, B1 >: B, A2, B2, A1T <: IT[A1]](
      mid: Middleware[R1, E1, A1, B1, A2, B2, A1T],
    ): Http.Total[R1, E1, A2, B2] =
      ApplyMiddlewareTotal(self, mid)

    /**
     * Named alias for `<>`
     */
    def orElse[R1 <: R, E1 >: E, A1 <: A, B1 >: B](
      other: Http.Total[R1, E1, A1, B1],
    ): Http.Total[R1, E1, A1, B1] =
      OrElseTotal(self, other)

    /**
     * Provides the environment to Http.
     */
    override def provideEnvironment(r: ZEnvironment[R]): Http.Total[Any, E, A, B] =
      AspectTotal(self, (z: ZIO[R, E, B]) => z.provideEnvironment(r))

    /**
     * Provides layer to Http.
     */
    override def provideLayer[E1 >: E, R0](
      layer: ZLayer[R0, E1, R],
    )(implicit trace: Trace): Http.Total[R0, E1, A, B] =
      AspectTotal(self, (z: ZIO[R, E1, B]) => z.provideLayer(layer))

    /**
     * Provides some of the environment to Http.
     */
    override def provideSomeEnvironment[R1](
      r: ZEnvironment[R1] => ZEnvironment[R],
    )(implicit trace: Trace): Http.Total[R1, E, A, B] =
      AspectTotal(self, (z: ZIO[R, E, B]) => z.provideSomeEnvironment[R1](r))

    /**
     * Provides some of the environment to Http leaving the remainder `R0`.
     */
    override def provideSomeLayer[R0, R1, E1 >: E](
      layer: ZLayer[R0, E1, R1],
    )(implicit ev: R0 with R1 <:< R, tagged: Tag[R1], trace: Trace): Http.Total[R0, E1, A, B] =
      AspectTotal(self, (z: ZIO[R, E1, B]) => z.provideSomeLayer[R0](layer))

    /**
     * Performs a race between two apps
     */
    def race[R1 <: R, E1 >: E, A1 <: A, B1 >: B](other: Http.Total[R1, E1, A1, B1]): Http.Total[R1, E1, A1, B1] =
      RaceTotal(self, other)

    /**
     * Combines the two apps and returns the result of the one on the right
     */
    override def zipRight[R1 <: R, E1 >: E, A1 <: A, C1](
      other: Http.Total[R1, E1, A1, C1],
    ): Http.Total[R1, E1, A1, C1] =
      self.flatMap(_ => other)
  }

  private final case class Succeed[B](b: B) extends Http.Total[Any, Nothing, Any, B] {

    override def apply(v1: Any): HExit[Any, Nothing, B] = HExit.succeed(b)
  }

  private final case class Race[R, E, A, B](self: Http[R, E, A, B], other: Http[R, E, A, B]) extends Http[R, E, A, B] {

    override def isDefinedAt(a: A): Boolean =
      self.isDefinedAt(a) || other.isDefinedAt(a)

    override def apply(a: A): HExit[R, E, B] =
      if (self.isDefinedAt(a) && !other.isDefinedAt(a)) self(a)
      else if (!self.isDefinedAt(a) && other.isDefinedAt(a)) other(a)
      else
        (self(a), other(a)) match {
          case (HExit.Effect(self), HExit.Effect(other)) =>
            Http.fromZIO(self.raceFirst(other)).apply(a)
          case (HExit.Effect(_), other)                  => other
          case (self, _)                                 => self
        }
  }

  private final case class RaceTotal[R, E, A, B](self: Http.Total[R, E, A, B], other: Http.Total[R, E, A, B])
      extends Http.Total[R, E, A, B] {

    override def apply(a: A): HExit[R, E, B] =
      (self(a), other(a)) match {
        case (HExit.Effect(self), HExit.Effect(other)) =>
          Http.fromZIO(self.raceFirst(other)).apply(a)
        case (HExit.Effect(_), other)                  => other
        case (self, _)                                 => self
      }
  }

  private final case class Fail[E](cause: Cause[E]) extends Http.Total[Any, E, Any, Nothing] {
    override def apply(v1: Any): HExit[Any, E, Nothing] = HExit.failCause(cause)
  }

  private final case class PartialHandler[R, E, A, B](f: PartialFunction[A, HExit[R, E, B]]) extends Http[R, E, A, B] {

    override def isDefinedAt(a: A): Boolean  = f.isDefinedAt(a)
    override def apply(a: A): HExit[R, E, B] =
      if (f.isDefinedAt(a))
        try f(a)
        catch {
          case NonFatal(e) => HExit.die(e)
        }
      else throw new MatchError(a)
  }

  private final case class TotalHandler[R, E, A, B](f: A => HExit[R, E, B]) extends Http.Total[R, E, A, B] {
    override def apply(a: A): HExit[R, E, B] =
      try f(a)
      catch { case NonFatal(e) => HExit.die(e) }

  }

  private final case class Chain[R, E, A, B, C](self: Http[R, E, A, B], other: Http.Total[R, E, B, C])
      extends Http[R, E, A, C] {

    override def isDefinedAt(a: A): Boolean =
      self.isDefinedAt(a)

    override def apply(a: A): HExit[R, E, C] =
      self(a).flatMap(other)
  }

  private final case class ChainTotal[R, E, A, B, C](self: Http.Total[R, E, A, B], other: Http.Total[R, E, B, C])
      extends Http.Total[R, E, A, C] {

    override def apply(a: A): HExit[R, E, C] =
      self(a).flatMap(other)
  }

  private final case class FoldHttp[R, E, EE, A, B, BB](
    self: Http[R, E, A, B],
    failure: Cause[E] => Http.Total[R, EE, A, BB],
    success: B => Http.Total[R, EE, A, BB],
  ) extends Http[R, EE, A, BB] {

    override def isDefinedAt(a: A): Boolean =
      self.isDefinedAt(a)

    override def apply(a: A): HExit[R, EE, BB] = {
      {
        try self(a)
        catch {
          case NonFatal(e) => HExit.die(e)
        }
      }.foldExit(
        failure(_)(a),
        success(_)(a),
      )
    }
  }

  private final case class FoldHttpTotal[R, E, EE, A, B, BB](
    self: Http.Total[R, E, A, B],
    failure: Cause[E] => Http.Total[R, EE, A, BB],
    success: B => Http.Total[R, EE, A, BB],
  ) extends Http.Total[R, EE, A, BB] {

    override def apply(a: A): HExit[R, EE, BB] = {
      {
        try self(a)
        catch {
          case NonFatal(e) => HExit.die(e)
        }
      }.foldExit(
        failure(_)(a),
        success(_)(a),
      )
    }
  }

  private case class Attempt[A](a: () => A) extends Http.Total[Any, Throwable, Any, A] {
    override def apply(v1: Any): HExit[Any, Throwable, A] =
      try HExit.succeed(a())
      catch { case NonFatal(e) => HExit.fail(e) }
  }

  private final case class Combine[R, E, EE >: E, A, B, BB >: B](
    self: Http[R, E, A, B],
    other: Http[R, EE, A, BB],
  ) extends Http[R, EE, A, BB] {

    override def isDefinedAt(a: A): Boolean =
      self.isDefinedAt(a) || other.isDefinedAt(a)

    override def apply(a: A): HExit[R, EE, BB] =
      applyOrElse(a, (a: A) => throw new MatchError(a))

    override def applyOrElse[A1 <: A, B1 >: HExit[R, E, BB]](x: A1, default: A1 => B1): B1 = {
      val z = self.applyOrElse(x, checkFallback[B1])
      if (!fallbackOccurred(z)) z else other.applyOrElse(x, default).asInstanceOf[B1]
    }
  }

  private case class CombineTotal[R, E, A, B, R1 <: R, E1 >: E, A1 <: A, B1 >: B](
    self: Http[R, E, A, B],
    other: Http.Total[R1, E1, A1, B1],
  ) extends Http.Total[R1, E1, A1, B1] {

    override def apply(a: A1): HExit[R1, E1, B1] = {
      val z = self.applyOrElse[A1, AnyRef](a, checkFallback[HExit[R1, E1, B1]])
      if (!fallbackOccurred(z)) z.asInstanceOf[HExit[R1, E1, B1]] else other(a)
    }
  }

  private final case class FromHExit[R, E, B](h: HExit[R, E, B]) extends Http.Total[R, E, Any, B] {
    override def apply(a: Any): HExit[R, E, B] = h
  }

  private final case class When[R, E, A, B](f: A => Boolean, other: Http[R, E, A, B]) extends Http[R, E, A, B] {

    override private[zio] def toHExitOrNull(a: A): HExit[R, E, B] =
      try applyOrElse(a, (_: A) => null)
      catch { case NonFatal(e) => HExit.die(e) }

    override def isDefinedAt(a: A): Boolean =
      f(a) && other.isDefinedAt(a)

    override def apply(a: A): HExit[R, E, B] = a match {
      case a if f(a) && other.isDefinedAt(a) => other(a)
    }

    override def applyOrElse[A1 <: A, B1 >: HExit[R, E, B]](x: A1, default: A1 => B1): B1 =
      if (f(x) && other.isDefinedAt(x)) other(x) else default(x)
  }

  private case object Empty extends Http[Any, Nothing, Any, Nothing] {
    override private[zio] def toHExitOrNull(a: Any): HExit[Any, Nothing, Nothing] = null

    override def isDefinedAt(a: Any): Boolean                = false
    override def apply(a: Any): HExit[Any, Nothing, Nothing] = throw new MatchError(a)
  }

  private final case class Identity[A]() extends Http.Total[Any, Nothing, A, A] {
    override def apply(a: A): HExit[Any, Nothing, A] = HExit.succeed(a)
  }

  private case class Aspect[R, E, A, B, R1, E1](
    self: Http[R, E, A, B],
    aspect: ZIO[R, E, B] => ZIO[R1, E1, B],
  ) extends Http[R1, E1, A, B] {

    override def isDefinedAt(a: A): Boolean =
      self.isDefinedAt(a)

    override def apply(a: A): HExit[R1, E1, B] =
      HExit.fromZIO(
        aspect(self(a).toZIO),
      )
  }

  private case class AspectTotal[R, E, A, B, R1, E1](
    self: Http.Total[R, E, A, B],
    aspect: ZIO[R, E, B] => ZIO[R1, E1, B],
  ) extends Http.Total[R1, E1, A, B] {

    override def apply(a: A): HExit[R1, E1, B] =
      HExit.fromZIO(
        aspect(self(a).toZIO),
      )
  }

  private case class OrElse[R, E, A, B](
    self: Http[R, E, A, B],
    other: Http[R, E, A, B],
  ) extends Http[R, E, A, B] {

    override def isDefinedAt(a: A): Boolean =
      self.isDefinedAt(a) || other.isDefinedAt(a)

    override def apply(a: A): HExit[R, E, B] = a match {
      case a if self.isDefinedAt(a) && !other.isDefinedAt(a) =>
        self(a)
      case a if !self.isDefinedAt(a) && other.isDefinedAt(a) =>
        other(a)
      case a if self.isDefinedAt(a) && other.isDefinedAt(a)  =>
        (self(a), other(a)) match {
          case (s @ HExit.Success(_), _)                        =>
            s
          case (s @ HExit.Failure(cause), _) if cause.isDie     =>
            s
          case (HExit.Failure(cause), other) if cause.isFailure =>
            other
          case (self, other)                                    =>
            HExit.fromZIO(self.toZIO.orElse(other.toZIO))
        }
    }
  }

  private case class OrElseTotal[R, E, A, B](
    self: Http.Total[R, E, A, B],
    other: Http.Total[R, E, A, B],
  ) extends Http.Total[R, E, A, B] {

    override def apply(a: A): HExit[R, E, B] =
      (self(a), other(a)) match {
        case (s @ HExit.Success(_), _)                        =>
          s
        case (s @ HExit.Failure(cause), _) if cause.isDie     =>
          s
        case (HExit.Failure(cause), other) if cause.isFailure =>
          other
        case (self, other)                                    =>
          HExit.fromZIO(self.toZIO.orElse(other.toZIO))
      }
  }

  private case class ApplyMiddleware[R, E, AIn, BIn, AOut, BOut, AInT <: IT[AIn]](
    self: Http[R, E, AIn, BIn],
    middleware: Middleware[R, E, AIn, BIn, AOut, BOut, AInT],
    canApplyToPartial: IT.CanApplyToPartial.Aux[AIn, AOut, AInT],
  ) extends Http[R, E, AOut, BOut] {

    override def isDefinedAt(a: AOut): Boolean =
      self.isDefinedAt(canApplyToPartial.transform(middleware.inputTransformation, a))

    override def apply(a: AOut): HExit[R, E, BOut] =
      middleware(self.withFallback(Http.dieMessage("Illegal state"))).apply(a)
  }

  private case class ApplyMiddlewareTotal[R, E, AIn, BIn, AOut, BOut, AInT <: IT[AIn]](
    self: Http.Total[R, E, AIn, BIn],
    middleware: Middleware[R, E, AIn, BIn, AOut, BOut, AInT],
  ) extends Http.Total[R, E, AOut, BOut] {
    override def apply(a: AOut): HExit[R, E, BOut] =
      middleware(self).apply(a)
  }

  private[this] val fallbackFn: AnyRef           = (_: Any) => fallbackFn
  private def checkFallback[B]: Any => B         = fallbackFn.asInstanceOf[Any => B]
  private def fallbackOccurred[B](x: B): Boolean = fallbackFn eq x.asInstanceOf[AnyRef]
}
