/*
 * Copyright 2023 the ZIO HTTP contributors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package zio.http

import zio._

import zio.http.codec.PathCodec

/**
 * Represents a collection of routes, each of which is defined by a pattern and
 * a handler. This data type can be thought of as modeling a routing table,
 * which decides where to direct every endpoint in an API based on both method
 * and path of the request.
 *
 * When you are done building a collection of routes, you typically convert the
 * routes into an [[zio.http.HttpApp]] value, which can be done with the
 * `toHttpApp` method.
 *
 * Routes may have handled or unhandled errors. A route of type `Route[Env,
 * Throwable]`, for example, has not handled its errors by converting them into
 * responses. Such unfinished routes cannot yet be converted into
 * [[zio.http.HttpApp]] values. First, you must handle errors with the
 * `handleError` or `handleErrorCause` methods.
 */
final class Routes[-Env, +Err] private (val routes: Chunk[zio.http.Route[Env, Err]]) { self =>

  /**
   * Returns the concatenation of these routes with the specified routes.
   */
  def ++[Env1 <: Env, Err1 >: Err](that: Routes[Env1, Err1]): Routes[Env1, Err1] =
    new Routes(self.routes ++ that.routes)

  /**
   * Appends the specified route to this collection of routes.
   */
  def :+[Env1 <: Env, Err1 >: Err](route: zio.http.Route[Env1, Err1]): Routes[Env1, Err1] =
    new Routes(routes :+ route)

  /**
   * Prepends the specified route to this collection of routes.
   */
  def +:[Env1 <: Env, Err1 >: Err](route: zio.http.Route[Env1, Err1]): Routes[Env1, Err1] =
    new Routes(route +: routes)

  def @@[Env1 <: Env](aspect: Middleware[Env1]): Routes[Env1, Err] =
    aspect(self)

  def apply(request: Request)(implicit ev: Err <:< Response, trace: Trace): ZIO[Env, Response, Response] =
    self.toHttpApp.apply(request)

  def asEnvType[Env2](implicit ev: Env2 <:< Env): Routes[Env2, Err] =
    self.asInstanceOf[Routes[Env2, Err]]

  def asErrorType[Err2](implicit ev: Err <:< Err2): Routes[Env, Err2] =
    self.asInstanceOf[Routes[Env, Err2]]

  /**
   * Handles all typed errors in the routes by converting them into responses.
   * This method can be used to convert routes that do not handle their errors
   * into ones that do handle their errors.
   */
  def handleError(f: Err => Response)(implicit trace: Trace): Routes[Env, Nothing] =
    new Routes(routes.map(_.handleError(f)))

  /**
   * Handles all typed errors, as well as all non-recoverable errors, by
   * converting them into responses. This method can be used to convert routes
   * that do not handle their errors into ones that do handle their errors.
   */
  def handleErrorCause(f: Cause[Err] => Response)(implicit trace: Trace): Routes[Env, Nothing] =
    new Routes(routes.map(_.handleErrorCause(f)))

  /**
   * Handles all typed errors, as well as all non-recoverable errors, by
   * converting them into a ZIO effect that produces the response. This method
   * can be used to convert routes that do not handle their errors into ones
   * that do handle their errors.
   */
  def handleErrorCauseZIO(f: Cause[Err] => ZIO[Any, Nothing, Response])(implicit trace: Trace): Routes[Env, Nothing] =
    new Routes(routes.map(_.handleErrorCauseZIO(f)))

  /**
   * Allows the transformation of the Err type through an Effectful program
   * allowing one to build up a Routes in Stages delegates to the Route
   */
  def mapErrorZIO[Err1](fxn: Err => ZIO[Any, Err1, Response])(implicit trace: Trace): Routes[Env, Err1] =
    new Routes(routes.map(_.mapErrorZIO(fxn)))

  /**
   * Allows the transformation of the Err type through a function allowing one
   * to build up a Routes in Stages delegates to the Route
   */
  def mapError[Err1](fxn: Err => Err1): Routes[Env, Err1] =
    new Routes(routes.map(_.mapError(fxn)))

  def nest(prefix: PathCodec[Unit])(implicit trace: Trace, ev: Err <:< Response): Routes[Env, Err] =
    new Routes(self.routes.map(_.nest(prefix)))

  /**
   * Handles all typed errors in the routes by converting them into responses,
   * taking into account the request that caused the error. This method can be
   * used to convert routes that do not handle their errors into ones that do
   * handle their errors.
   */
  def handleErrorRequest(f: (Err, Request) => Response)(implicit trace: Trace): Routes[Env, Nothing] =
    new Routes(routes.map(_.handleErrorRequest(f)))

  /**
   * Handles all typed errors in the routes by converting them into responses,
   * taking into account the request that caused the error. This method can be
   * used to convert routes that do not handle their errors into ones that do
   * handle their errors.
   */
  def handleErrorRequestCause(f: (Request, Cause[Err]) => Response)(implicit trace: Trace): Routes[Env, Nothing] =
    new Routes(routes.map(_.handleErrorRequestCause(f)))

  /**
   * Handles all typed errors, as well as all non-recoverable errors, by
   * converting them into a ZIO effect that produces the response, taking into
   * account the request that caused the error. This method can be used to
   * convert routes that do not handle their errors into ones that do handle
   * their errors.
   */
  def handleErrorRequestCauseZIO(f: (Request, Cause[Err]) => ZIO[Any, Nothing, Response])(implicit
    trace: Trace,
  ): Routes[Env, Nothing] =
    new Routes(routes.map(_.handleErrorRequestCauseZIO(f)))

  /**
   * Returns new routes that have each been provided the specified environment,
   * thus eliminating their requirement for any specific environment.
   */
  def provideEnvironment(env: ZEnvironment[Env]): Routes[Any, Err] =
    new Routes(routes.map(_.provideEnvironment(env)))

  def run(request: Request)(implicit trace: Trace): ZIO[Env, Either[Err, Response], Response] = {

    class RouteFailure[+Err](val err: Cause[Err]) extends Throwable(null, null, true, false) {
      override def getMessage: String = err.unified.headOption.fold("<unknown>")(_.message)

      override def getStackTrace(): Array[StackTraceElement] =
        err.unified.headOption.fold[Chunk[StackTraceElement]](Chunk.empty)(_.trace).toArray

      override def getCause(): Throwable =
        err.find { case Cause.Die(throwable, _) => throwable }
          .orElse(err.find { case Cause.Fail(value: Throwable, _) => value })
          .orNull

      def fillSuppressed()(implicit unsafe: Unsafe): Unit =
        if (getSuppressed().length == 0) {
          err.unified.iterator.drop(1).foreach(unified => addSuppressed(unified.toThrowable))
        }

      override def toString =
        err.prettyPrint
    }
    var routeFailure: RouteFailure[Err] = null

    handleErrorCauseZIO { cause =>
      routeFailure = new RouteFailure(cause)
      ZIO.refailCause(Cause.die(routeFailure))
    }
      .apply(request)
      .mapErrorCause {
        case Cause.Die(value: RouteFailure[_], _) if value == routeFailure => routeFailure.err.map(Left(_))
        case cause                                                         => cause.map(Right(_))
      }
  }

  /**
   * Returns new routes that automatically translate all failures into
   * responses, using best-effort heuristics to determine the appropriate HTTP
   * status code, and attaching error details using the HTTP header `Warning`.
   */
  def sandbox(implicit trace: Trace): Routes[Env, Nothing] =
    new Routes(routes.map(_.sandbox))

  /**
   * Returns new routes that are all timed out by the specified maximum
   * duration.
   */
  def timeout(duration: Duration)(implicit trace: Trace): Routes[Env, Err] =
    self @@ Middleware.timeout(duration)

  /**
   * Converts the routes into an app, which can be done only when errors are
   * handled and converted into responses.
   */
  def toHttpApp(implicit ev: Err <:< Response): HttpApp[Env] =
    HttpApp(asErrorType[Response])

  /**
   * Returns new routes whose handlers are transformed by the specified
   * function.
   */
  def transform[Env1](
    f: Handler[Env, Response, Request, Response] => Handler[Env1, Response, Request, Response],
  ): Routes[Env1, Err] =
    new Routes(routes.map(_.transform(f)))
}
object Routes {

  case class Builder[Env, Ctx, Err](routes: Seq[Route.Builder[Env, _, Ctx, _, Err]]) {
    def @@(aspect: HandlerAspect[Env, Ctx]): Routes[Env, Err] =
      new Routes(Chunk.fromIterable(routes.map(_ @@ aspect)))
  }

  /**
   * Constructs new routes from a varargs of individual routes.
   */
  def apply[Env, Err](route: zio.http.Route[Env, Err], routes: zio.http.Route[Env, Err]*): Routes[Env, Err] =
    new Routes(Chunk(route) ++ Chunk.fromIterable(routes))

  def build[Env, Err, Ctx](
    route: Route.Builder[Env, _, Ctx, _, Err],
    routes: Route.Builder[Env, _, Ctx, _, Err]*,
  ): Routes.Builder[Env, Ctx, Err] =
    Routes.Builder[Env, Ctx, Err](route +: routes)

  /**
   * A empty routes value that contains no routes inside it.
   */
  val empty: Routes[Any, Nothing] = new Routes(Chunk.empty)

  /**
   * Constructs new routes from an iterable of individual routes.
   */
  def fromIterable[Env, Err](iterable: Iterable[Route[Env, Err]]): Routes[Env, Err] =
    new Routes(Chunk.fromIterable(iterable))

  /**
   * Constructs a singleton route from a handler that handles all possible
   * methods and paths. You would only use this method for testing.
   */
  def singleton[Env, Err](h: Handler[Env, Err, (Path, Request), Response])(implicit trace: Trace): Routes[Env, Err] =
    Routes(Route.route(RoutePattern.any)(h))
}
