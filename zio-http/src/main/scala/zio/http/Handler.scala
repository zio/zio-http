/*
 * Copyright 2021 - 2023 Sporta Technologies PVT LTD & the ZIO HTTP contributors.
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
import zio.http.template.{Html, Template}
import zio.http.Header.HeaderType
import zio.http.internal.HeaderModifier
import zio.stream.ZStream

import java.io.{File, FileNotFoundException, IOException}
import java.nio.charset.Charset
import java.nio.file.{AccessDeniedException, NotDirectoryException}
import scala.reflect.ClassTag
import scala.util.control.NonFatal // scalafix:ok;
import java.util.zip.ZipFile
import zio.stacktracer.TracingImplicits.disableAutoTrace
sealed trait Handler[-R, +Err, -In, +Out] { self =>

  def @@[Env1 <: R, Ctx, In1 <: In](aspect: HandlerAspect[Env1, Unit])(implicit
    in: In1 <:< Request,
    out: Out <:< Response,
    err: Err <:< Response,
  ): Handler[Env1, Response, Request, Response] = {
    def convert(handler: Handler[R, Err, In, Out]): Handler[R, Response, Request, Response] =
      handler.asInstanceOf[Handler[R, Response, Request, Response]]

    aspect.applyHandler(convert(self))
  }

  def @@[Env1 <: R, Ctx, In1 <: In](aspect: HandlerAspect[Env1, Ctx])(implicit
    zippable: Zippable.Out[Ctx, Request, In1],
    res: Out <:< Response,
    trace: Trace,
  ): Handler[Env1, Response, Request, Response] = {
    def convert(handler: Handler[R, Err, In, Out]): Handler[R, Response, In1, Response] =
      handler.asInstanceOf[Handler[R, Response, In1, Response]]

    aspect.applyHandlerContext(Handler.fromFunction[(Ctx, Request)] { case (ctx, req) =>
      zippable.zip(ctx, req)
    } >>> convert(self))
  }

  /**
   * Alias for flatmap
   */
  final def >>=[R1 <: R, Err1 >: Err, In1 <: In, Out1](
    f: Out => Handler[R1, Err1, In1, Out1],
  )(implicit trace: Trace): Handler[R1, Err1, In1, Out1] =
    self.flatMap(f)

  /**
   * Pipes the output of one handler into the other
   */
  final def >>>[R1 <: R, Err1 >: Err, In1 >: Out, Out1](
    that: Handler[R1, Err1, In1, Out1],
  )(implicit trace: Trace): Handler[R1, Err1, In, Out1] =
    self andThen that

  /**
   * Composes one handler with another.
   */
  final def <<<[R1 <: R, Err1 >: Err, In1, Out1 <: In](
    that: Handler[R1, Err1, In1, Out1],
  )(implicit trace: Trace): Handler[R1, Err1, In1, Out] =
    self compose that

  /**
   * Runs self but if it fails, runs other, ignoring the result from self.
   */
  final def <>[R1 <: R, Err1, In1 <: In, Out1 >: Out](
    that: Handler[R1, Err1, In1, Out1],
  )(implicit trace: Trace): Handler[R1, Err1, In1, Out1] =
    self.orElse(that)

  final def <*>[R1 <: R, Err1 >: Err, In1 <: In, Out1](
    that: Handler[R1, Err1, In1, Out1],
  )(implicit trace: Trace): Handler[R1, Err1, In1, (Out, Out1)] =
    self.zip(that)

  /**
   * Alias for zipLeft
   */
  final def <*[R1 <: R, Err1 >: Err, In1 <: In, Out1](
    that: Handler[R1, Err1, In1, Out1],
  )(implicit trace: Trace): Handler[R1, Err1, In1, Out] =
    self.zipLeft(that)

  /**
   * Alias for zipRight
   */
  final def *>[R1 <: R, Err1 >: Err, In1 <: In, Out1](
    that: Handler[R1, Err1, In1, Out1],
  )(implicit trace: Trace): Handler[R1, Err1, In1, Out1] =
    self.zipRight(that)

  /**
   * Returns a handler that submerges the error case of an `Either` into the
   * `Handler`. The inverse operation of `Handler.either`.
   */
  final def absolve[Err1 >: Err, Out1](implicit
    ev: Out <:< Either[Err1, Out1],
    trace: Trace,
  ): Handler[R, Err1, In, Out1] =
    self.flatMap { out =>
      ev(out) match {
        case Right(out1) => Handler.succeed(out1)
        case Left(err)   => Handler.fail(err)
      }
    }

  /**
   * Named alias for `>>>`
   */
  final def andThen[R1 <: R, Err1 >: Err, In1 >: Out, Out1](
    that: Handler[R1, Err1, In1, Out1],
  )(implicit trace: Trace): Handler[R1, Err1, In, Out1] =
    new Handler[R1, Err1, In, Out1] {
      override def apply(in: In): ZIO[R1, Err1, Out1] =
        self(in).flatMap(that(_))
    }

  /**
   * Consumes the input and executes the Handler.
   */
  def apply(in: In): ZIO[R, Err, Out]

  /**
   * Makes the handler resolve with a constant value
   */
  final def as[Out1](out: Out1)(implicit trace: Trace): Handler[R, Err, In, Out1] =
    self.map(_ => out)

  final def asEnvType[R2](implicit ev: R2 <:< R): Handler[R2, Err, In, Out] =
    self.asInstanceOf[Handler[R2, Err, In, Out]]

  final def asErrorType[Err2](implicit ev: Err <:< Err2): Handler[R, Err2, In, Out] =
    self.asInstanceOf[Handler[R, Err2, In, Out]]

  final def asInType[In2](implicit ev: In2 <:< In): Handler[R, Err, In2, Out] =
    self.asInstanceOf[Handler[R, Err, In2, Out]]

  final def asOutType[Out2](implicit ev: Out <:< Out2): Handler[R, Err, In, Out2] =
    self.asInstanceOf[Handler[R, Err, In, Out2]]

  final def body(implicit ev: Out <:< Response, trace: Trace): Handler[R, Err, In, Body] =
    self.map(_.body)

  /**
   * Catches all the exceptions that the handler can fail with
   */
  final def catchAll[R1 <: R, Err1, In1 <: In, Out1 >: Out](f: Err => Handler[R1, Err1, In1, Out1])(implicit
    trace: Trace,
  ): Handler[R1, Err1, In1, Out1] =
    self.foldHandler(f, Handler.succeed(_))

  final def catchAllCause[R1 <: R, Err1, In1 <: In, Out1 >: Out](f: Cause[Err] => Handler[R1, Err1, In1, Out1])(implicit
    trace: Trace,
  ): Handler[R1, Err1, In1, Out1] =
    self.foldCauseHandler(f, Handler.succeed(_))

  /**
   * Recovers from all defects with provided function.
   *
   * '''WARNING''': There is no sensible way to recover from defects. This
   * method should be used only at the boundary between `Handler` and an
   * external system, to transmit information on a defect for diagnostic or
   * explanatory purposes.
   */
  final def catchAllDefect[R1 <: R, Err1 >: Err, In1 <: In, Out1 >: Out](f: Throwable => Handler[R1, Err1, In1, Out1])(
    implicit trace: Trace,
  ): Handler[R1, Err1, In1, Out1] =
    self.foldCauseHandler(
      cause => cause.dieOption.fold[Handler[R1, Err1, In1, Out1]](Handler.failCause(cause))(f),
      Handler.succeed(_),
    )

  /**
   * Recovers from some or all of the error cases.
   */
  final def catchSome[R1 <: R, Err1 >: Err, In1 <: In, Out1 >: Out](
    pf: PartialFunction[Err, Handler[R1, Err1, In1, Out1]],
  )(implicit
    trace: Trace,
  ): Handler[R1, Err1, In1, Out1] =
    self.catchAll(err => pf.applyOrElse(err, (err: Err1) => Handler.fail(err)))

  /**
   * Recovers from some or all of the defects with provided partial function.
   *
   * '''WARNING''': There is no sensible way to recover from defects. This
   * method should be used only at the boundary between `Handler` and an
   * external system, to transmit information on a defect for diagnostic or
   * explanatory purposes.
   */
  final def catchSomeDefect[R1 <: R, Err1 >: Err, In1 <: In, Out1 >: Out](
    pf: PartialFunction[Throwable, Handler[R1, Err1, In1, Out1]],
  )(implicit
    trace: Trace,
  ): Handler[R1, Err1, In1, Out1] =
    self.catchAllDefect(err => pf.applyOrElse(err, (cause: Throwable) => Handler.die(cause)))

  /**
   * Named alias for `<<<`
   */
  final def compose[R1 <: R, Err1 >: Err, In1, Out1 <: In](
    that: Handler[R1, Err1, In1, Out1],
  )(implicit trace: Trace): Handler[R1, Err1, In1, Out] =
    that.andThen(self)

  /**
   * Transforms the input of the handler before passing it on to the current
   * Handler
   */
  final def contramap[In1](f: In1 => In): Handler[R, Err, In1, Out] =
    new Handler[R, Err, In1, Out] {
      override def apply(in: In1): ZIO[R, Err, Out] =
        self(f(in))
    }

  /**
   * Transforms the input of the handler before giving it effectfully
   */
  final def contramapZIO[R1 <: R, Err1 >: Err, In1](f: In1 => ZIO[R1, Err1, In])(implicit
    trace: Trace,
  ): Handler[R1, Err1, In1, Out] =
    new Handler[R1, Err1, In1, Out] {
      override def apply(in: In1): ZIO[R1, Err1, Out] =
        f(in).flatMap(self(_))
    }

  /**
   * Transforms the input of the handler before passing it on to the current
   * Handler
   */
  final def contraFlatMap[In1]: Handler.ContraFlatMap[R, Err, In, Out, In1] =
    new Handler.ContraFlatMap(self)

  /**
   * Delays production of output B for the specified duration of time
   */
  final def delay(duration: Duration)(implicit trace: Trace): Handler[R, Err, In, Out] =
    self.delayAfter(duration)

  /**
   * Delays production of output B for the specified duration of time
   */
  final def delayAfter(duration: Duration)(implicit trace: Trace): Handler[R, Err, In, Out] =
    self.mapZIO(out => ZIO.succeed(out).delay(duration))

  /**
   * Delays consumption of input A for the specified duration of time
   */
  final def delayBefore(duration: Duration)(implicit trace: Trace): Handler[R, Err, In, Out] =
    self.contramapZIO(in => ZIO.succeed(in).delay(duration))

  /**
   * Returns a handler whose failure and success have been lifted into an
   * `Either`. The resulting handler cannot fail, because the failure case has
   * been exposed as part of the `Either` success case.
   */
  final def either(implicit ev: CanFail[Err], trace: Trace): Handler[R, Nothing, In, Either[Err, Out]] =
    self.foldHandler(err => Handler.succeed(Left(err)), out => Handler.succeed(Right(out)))

  /**
   * Flattens a handler of a handler
   */
  final def flatten[R1 <: R, Err1 >: Err, In1 <: In, Out1](implicit
    ev: Out <:< Handler[R1, Err1, In1, Out1],
    trace: Trace,
  ): Handler[R1, Err1, In1, Out1] =
    self.flatMap(identity(_))

  /**
   * Creates a new handler from another
   */
  final def flatMap[R1 <: R, Err1 >: Err, In1 <: In, Out1](
    f: Out => Handler[R1, Err1, In1, Out1],
  )(implicit trace: Trace): Handler[R1, Err1, In1, Out1] =
    self.foldHandler(
      Handler.fail(_),
      f(_),
    )

  final def foldCauseHandler[R1 <: R, Err1, In1 <: In, Out1](
    onFailure: Cause[Err] => Handler[R1, Err1, In1, Out1],
    onSuccess: Out => Handler[R1, Err1, In1, Out1],
  )(implicit trace: Trace): Handler[R1, Err1, In1, Out1] =
    new Handler[R1, Err1, In1, Out1] {
      override def apply(in: In1): ZIO[R1, Err1, Out1] =
        self(in).foldCauseZIO(
          cause => onFailure(cause)(in),
          out => onSuccess(out)(in),
        )
    }

  /**
   * Folds over the handler by taking in two functions one for success and one
   * for failure respectively.
   */
  final def foldHandler[R1 <: R, Err1, In1 <: In, Out1](
    onFailure: Err => Handler[R1, Err1, In1, Out1],
    onSuccess: Out => Handler[R1, Err1, In1, Out1],
  )(implicit trace: Trace): Handler[R1, Err1, In1, Out1] =
    self.foldCauseHandler(
      cause => cause.failureOrCause.fold(onFailure, Handler.failCause(_)),
      onSuccess,
    )

  final def headers(implicit ev: Out <:< Response, trace: Trace): Handler[R, Err, In, Headers] =
    self.map(_.headers)

  final def header(headerType: HeaderType)(implicit
    ev: Out <:< Response,
    trace: Trace,
  ): Handler[R, Err, In, Option[headerType.HeaderValue]] =
    self.headers.map(_.get(headerType))

  /**
   * Transforms the output of the handler
   */
  final def map[Out1](f: Out => Out1)(implicit trace: Trace): Handler[R, Err, In, Out1] =
    self >>> Handler.fromFunction(f)

  /**
   * Transforms the failure of the handler
   */
  final def mapError[Err1](f: Err => Err1)(implicit trace: Trace): Handler[R, Err1, In, Out] =
    self.foldHandler(err => Handler.fail(f(err)), Handler.succeed(_))

  /**
   * Transforms all failures except pure interruption.
   */
  final def mapErrorCause[Err2](f: Cause[Err] => Err2)(implicit trace: Trace): Handler[R, Err2, In, Out] =
    self.foldCauseHandler(
      err => if (err.isInterruptedOnly) Handler.failCause(err.asInstanceOf[Cause[Nothing]]) else Handler.fail(f(err)),
      Handler.succeed(_),
    )

  /**
   * Transforms the output of the handler effectfully
   */
  final def mapZIO[R1 <: R, Err1 >: Err, Out1](f: Out => ZIO[R1, Err1, Out1])(implicit
    trace: Trace,
  ): Handler[R1, Err1, In, Out1] =
    self >>> Handler.fromFunctionZIO(f)

  /**
   * Transforms the failure of the handler effectfully
   */
  final def mapErrorZIO[R1 <: R, Err1, Out1 >: Out](f: Err => ZIO[R1, Err1, Out1])(implicit
    trace: Trace,
  ): Handler[R1, Err1, In, Out1] =
    self.foldHandler(err => Handler.fromZIO(f(err)), Handler.succeed(_))

  /**
   * Transforms all failures except effectfull interruption.
   */
  final def mapErrorCauseZIO[R1 <: R, Err1, Out1 >: Out](
    f: Cause[Err] => ZIO[R1, Err1, Out1],
  )(implicit trace: Trace): Handler[R1, Err1, In, Out1] =
    self.foldCauseHandler(
      err =>
        if (err.isInterruptedOnly) Handler.fromZIO(f(err.asInstanceOf[Cause[Nothing]])) else Handler.fromZIO(f(err)),
      Handler.succeed(_),
    )

  /**
   * Returns a new handler where the error channel has been merged into the
   * success channel to their common combined type.
   */
  final def merge[Err1 >: Err, Out1 >: Out](implicit ev: Err1 =:= Out1, trace: Trace): Handler[R, Nothing, In, Out1] =
    self.catchAll(Handler.succeed(_))

  /**
   * Narrows the type of the input
   */
  final def narrow[In1](implicit ev: In1 <:< In): Handler[R, Err, In1, Out] =
    self.asInstanceOf[Handler[R, Err, In1, Out]]

  final def onExit[R1 <: R, Err1 >: Err](f: Exit[Err, Out] => ZIO[R1, Err1, Any])(implicit
    trace: Trace,
  ): Handler[R1, Err1, In, Out] =
    self.tapAllZIO(
      cause => f(Exit.failCause(cause)),
      out => f(Exit.succeed(out)),
    )

  /**
   * Executes this handler, skipping the error but returning optionally the
   * success.
   */
  final def option(implicit ev: CanFail[Err], trace: Trace): Handler[R, Nothing, In, Option[Out]] =
    self.foldHandler(_ => Handler.succeed(None), out => Handler.succeed(Some(out)))

  /**
   * Converts an option on errors into an option on values.
   */
  final def optional[Err1](implicit ev: Err <:< Option[Err1], trace: Trace): Handler[R, Err1, In, Option[Out]] =
    self.foldHandler(
      err => ev(err).fold[Handler[R, Err1, In, Option[Out]]](Handler.succeed(None))(Handler.fail(_)),
      out => Handler.succeed(Some(out)),
    )

  /**
   * Translates handler failure into death of the handler, making all failures
   * unchecked and not a part of the type of the handler.
   */
  final def orDie(implicit ev1: Err <:< Throwable, ev2: CanFail[Err], trace: Trace): Handler[R, Nothing, In, Out] =
    orDieWith(ev1)

  /**
   * Keeps none of the errors, and terminates the handler with them, using the
   * specified function to convert the `E` into a `Throwable`.
   */
  final def orDieWith(f: Err => Throwable)(implicit ev: CanFail[Err], trace: Trace): Handler[R, Nothing, In, Out] =
    self.foldHandler(err => Handler.die(f(err)), Handler.succeed(_))

  /**
   * Named alias for `<>`
   */
  final def orElse[R1 <: R, Err1, In1 <: In, Out1 >: Out](
    that: Handler[R1, Err1, In1, Out1],
  )(implicit trace: Trace): Handler[R1, Err1, In1, Out1] =
    new Handler[R1, Err1, In1, Out1] {
      override def apply(in: In1): ZIO[R1, Err1, Out1] =
        (self(in), that(in)) match {
          case (s @ Exit.Success(_), _)                        =>
            s
          case (Exit.Failure(cause), _) if cause.isDie         =>
            Exit.die(cause.dieOption.get)
          case (Exit.Failure(cause), other) if cause.isFailure =>
            other
          case (self, other)                                   =>
            self.orElse(other)
        }
    }

  /**
   * Provides the environment to Handler.
   */
  final def provideEnvironment(r: ZEnvironment[R])(implicit trace: Trace): Handler[Any, Err, In, Out] =
    new Handler[Any, Err, In, Out] {
      override def apply(in: In): ZIO[Any, Err, Out] =
        self(in).provideEnvironment(r)
    }

  /**
   * Provides layer to Handler.
   */
  final def provideLayer[Err1 >: Err, R0](layer: ZLayer[R0, Err1, R])(implicit
    trace: Trace,
  ): Handler[R0, Err1, In, Out] =
    new Handler[R0, Err1, In, Out] {
      override def apply(in: In): ZIO[R0, Err1, Out] =
        self(in).provideLayer(layer)
    }

  /**
   * Provides some of the environment to Handler.
   */
  final def provideSomeEnvironment[R1](f: ZEnvironment[R1] => ZEnvironment[R])(implicit
    trace: Trace,
  ): Handler[R1, Err, In, Out] =
    new Handler[R1, Err, In, Out] {
      override def apply(in: In): ZIO[R1, Err, Out] =
        self(in).provideSomeEnvironment(f)
    }

  /**
   * Provides some of the environment to Handler leaving the remainder `R0`.
   */
  final def provideSomeLayer[R0, R1: Tag, Err1 >: Err](
    layer: ZLayer[R0, Err1, R1],
  )(implicit ev: R0 with R1 <:< R, trace: Trace): Handler[R0, Err1, In, Out] =
    new Handler[R0, Err1, In, Out] {
      override def apply(in: In): ZIO[R0, Err1, Out] =
        self(in).provideSomeLayer(layer)
    }

  /**
   * Performs a race between two handlers
   */
  final def race[R1 <: R, Err1 >: Err, In1 <: In, Out1 >: Out](
    that: Handler[R1, Err1, In1, Out1],
  )(implicit trace: Trace): Handler[R1, Err1, In1, Out1] =
    new Handler[R1, Err1, In1, Out1] {
      override def apply(in: In1): ZIO[R1, Err1, Out1] =
        (self(in), that(in)) match {
          case (self: Exit[Err, Out], _)    => self
          case (_, other: Exit[Err1, Out1]) => other
          case (self, other)                => self.raceFirst(other)
        }
    }

  /**
   * Keeps some of the errors, and terminates the handler with the rest.
   */
  final def refineOrDie[Err1](
    pf: PartialFunction[Err, Err1],
  )(implicit ev1: Err <:< Throwable, ev2: CanFail[Err], trace: Trace): Handler[R, Err1, In, Out] =
    refineOrDieWith(pf)(ev1)

  /**
   * Keeps some of the errors, and terminates the handler with the rest, using
   * the specified function to convert the `E` into a `Throwable`.
   */
  final def refineOrDieWith[Err1](
    pf: PartialFunction[Err, Err1],
  )(f: Err => Throwable)(implicit ev: CanFail[Err], trace: Trace): Handler[R, Err1, In, Out] =
    self.foldHandler(
      err => pf.andThen(Handler.fail(_)).applyOrElse(err, (e: Err) => Handler.die(f(e))),
      Handler.succeed(_),
    )

  final def run(
    method: Method = Method.GET,
    path: Path = Path.root,
    headers: Headers = Headers.empty,
    body: Body = Body.empty,
  )(implicit ev: Request <:< In): ZIO[R, Err, Out] =
    self(ev(Request(method = method, url = URL.root.path(path), headers = headers, body = body)))

  final def runZIO(in: In): ZIO[R, Err, Out] =
    self(in)

  final def sandbox(implicit trace: Trace): Handler[R, Response, In, Out] =
    self.mapErrorCause(Response.fromCause(_))

  final def status(implicit ev: Out <:< Response, trace: Trace): Handler[R, Err, In, Status] =
    self.map(_.status)

  /**
   * Returns a Handler that effectfully peeks at the success, failed or
   * defective value of this Handler.
   */
  final def tapAllZIO[R1 <: R, Err1 >: Err](
    onFailure: Cause[Err] => ZIO[R1, Err1, Any],
    onSuccess: Out => ZIO[R1, Err1, Any],
  )(implicit trace: Trace): Handler[R1, Err1, In, Out] =
    new Handler[R1, Err1, In, Out] {
      override def apply(in: In): ZIO[R1, Err1, Out] =
        self(in) match {
          case Exit.Success(a)     => onSuccess(a).as(a)
          case Exit.Failure(cause) => onFailure(cause) *> ZIO.failCause(cause)
          case z                   => z.tapErrorCause(onFailure).tap(onSuccess)
        }
    }

  final def tapErrorCauseZIO[R1 <: R, Err1 >: Err](
    f: Cause[Err] => ZIO[R1, Err1, Any],
  )(implicit trace: Trace): Handler[R1, Err1, In, Out] =
    self.tapAllZIO(f, _ => ZIO.unit)

  /**
   * Returns a Handler that effectfully peeks at the failure of this Handler.
   */
  final def tapErrorZIO[R1 <: R, Err1 >: Err](
    f: Err => ZIO[R1, Err1, Any],
  )(implicit trace: Trace): Handler[R1, Err1, In, Out] =
    self.tapAllZIO(cause => cause.failureOption.fold[ZIO[R1, Err1, Any]](ZIO.unit)(f), _ => ZIO.unit)

  /**
   * Returns a Handler that effectfully peeks at the success of this Handler.
   */
  final def tapZIO[R1 <: R, Err1 >: Err](f: Out => ZIO[R1, Err1, Any])(implicit
    trace: Trace,
  ): Handler[R1, Err1, In, Out] =
    self.tapAllZIO(_ => ZIO.unit, f)

  def timeout(duration: Duration)(implicit trace: Trace): Handler[R, Err, In, Option[Out]] =
    Handler.fromFunctionZIO[In] { request =>
      self(request).timeout(duration)
    }

  def timeoutFail[Out1 >: Out](out: Out1)(duration: Duration)(implicit trace: Trace): Handler[R, Err, In, Out1] =
    Handler.fromFunctionZIO[In] { request =>
      self(request).timeout(duration).map(_.getOrElse(out))
    }

  /**
   * Converts the request handler into an HTTP application. Note that the
   * handler of the HTTP application is not identical to this handler, because
   * the handler has been appropriately sandboxed, turning all possible failures
   * into well-formed HTTP responses.
   */
  def toHttpApp(implicit err: Err <:< Response, in: Request <:< In, out: Out <:< Response, trace: Trace): HttpApp[R] = {
    val handler: Handler[R, Response, Request, Response] =
      self.asInstanceOf[Handler[R, Response, Request, Response]]

    HttpApp(Routes.singleton(handler.contramap[(Path, Request)](_._2)))
  }

  /**
   * Takes some defects and converts them into failures.
   */
  final def unrefine[Err1 >: Err](pf: PartialFunction[Throwable, Err1])(implicit
    trace: Trace,
  ): Handler[R, Err1, In, Out] =
    unrefineWith(pf)(err => err)

  /**
   * Takes some defects and converts them into failures.
   */
  final def unrefineTo[Err1 >: Err: ClassTag](implicit trace: Trace): Handler[R, Err1, In, Out] = {
    val pf: PartialFunction[Throwable, Err1] = { case err: Err1 =>
      err
    }
    unrefine(pf)
  }

  /**
   * Takes some defects and converts them into failures, using the specified
   * function to convert the `E` into an `E1`.
   */
  final def unrefineWith[Err1](
    pf: PartialFunction[Throwable, Err1],
  )(f: Err => Err1)(implicit trace: Trace): Handler[R, Err1, In, Out] =
    self.catchAllCause(cause =>
      cause.find {
        case Cause.Die(t, _) if pf.isDefinedAt(t) => pf(t)
      }.fold(Handler.failCause(cause.map(f)))(Handler.fail(_)),
    )

  /**
   * Unwraps a Handler that returns a ZIO of Http
   */
  final def unwrapZIO[R1 <: R, Err1 >: Err, Out1](implicit
    ev: Out <:< ZIO[R1, Err1, Out1],
    trace: Trace,
  ): Handler[R1, Err1, In, Out1] =
    self.flatMap(out => Handler.fromZIO(ev(out)))

  /**
   * Widens the type of the output
   */
  def widen[Err1, Out1](implicit ev1: Err <:< Err1, ev2: Out <:< Out1): Handler[R, Err1, In, Out1] =
    self.asInstanceOf[Handler[R, Err1, In, Out1]]

  final def zip[R1 <: R, Err1 >: Err, In1 <: In, Out1](
    that: Handler[R1, Err1, In1, Out1],
  )(implicit trace: Trace): Handler[R1, Err1, In1, (Out, Out1)] =
    self.flatMap(out => that.map(out1 => (out, out1)))

  final def zipLeft[R1 <: R, Err1 >: Err, In1 <: In, Out1](
    that: Handler[R1, Err1, In1, Out1],
  )(implicit trace: Trace): Handler[R1, Err1, In1, Out] =
    self.flatMap(out => that.as(out))

  /**
   * Combines the two apps and returns the result of the one on the right
   */
  final def zipRight[R1 <: R, Err1 >: Err, In1 <: In, Out1](
    that: Handler[R1, Err1, In1, Out1],
  )(implicit trace: Trace): Handler[R1, Err1, In1, Out1] =
    self.flatMap(_ => that)
}

object Handler {

  def asChunkBounded(request: Request, limit: Int)(implicit trace: Trace): Handler[Any, Throwable, Any, Chunk[Byte]] =
    Handler.fromZIO(
      request.body.asStream.chunks
        .runFoldZIO(Chunk.empty[Byte]) { case (acc, bytes) =>
          ZIO
            .succeed(acc ++ bytes)
            .filterOrFail(_.size < limit)(new Exception("Too large input"))
        },
    )

  /**
   * Attempts to create a Handler that succeeds with the provided value,
   * capturing all exceptions on it's way.
   */
  def attempt[Out](out: => Out): Handler[Any, Throwable, Any, Out] =
    fromExit {
      try Exit.succeed(out)
      catch {
        case NonFatal(cause) => Exit.fail(cause)
      }
    }

  /**
   * Creates a handler which always responds with a 400 status code.
   */
  def badRequest: Handler[Any, Nothing, Any, Response] =
    error(Status.BadRequest)

  /**
   * Creates a handler which always responds with a 400 status code.
   */
  def badRequest(message: => String): Handler[Any, Nothing, Any, Response] =
    error(Status.BadRequest, message)

  /**
   * Returns a handler that dies with the specified `Throwable`. This method can
   * be used for terminating an handler because a defect has been detected in
   * the code. Terminating a handler leads to aborting handling of an HTTP
   * request and responding with 500 Internal Server Error.
   */
  def die(failure: => Throwable): Handler[Any, Nothing, Any, Nothing] =
    fromExit(Exit.die(failure))

  /**
   * Returns an handler that dies with a `RuntimeException` having the specified
   * text message. This method can be used for terminating a HTTP request
   * because a defect has been detected in the code.
   */
  def dieMessage(message: => String): Handler[Any, Nothing, Any, Nothing] =
    die(new RuntimeException(message))

  /**
   * Creates a handler with an error and the default error message.
   */
  def error(status: => Status.Error): Handler[Any, Nothing, Any, Response] =
    response(Response.error(status))

  /**
   * Creates a handler with an error and the specified error message.
   */
  def error(status: => Status.Error, message: => String): Handler[Any, Nothing, Any, Response] =
    response(Response.error(status, message))

  /**
   * Creates a Handler that always fails
   */
  def fail[Err](err: => Err): Handler[Any, Err, Any, Nothing] =
    fromExit(Exit.fail(err))

  def failCause[Err](cause: => Cause[Err]): Handler[Any, Err, Any, Nothing] =
    fromExit(Exit.failCause(cause))

  def firstSuccessOf[R, Err, In, Out](
    handlers: NonEmptyChunk[Handler[R, Err, In, Out]],
    isRecoverable: Cause[Err] => Boolean = (cause: Cause[Err]) => !cause.isDie,
  )(implicit trace: Trace): Handler[R, Err, In, Out] =
    handlers.tail.foldLeft[Handler[R, Err, In, Out]](handlers.head) { (acc, handler) =>
      acc.catchAllCause { cause =>
        if (isRecoverable(cause)) {
          handler
        } else {
          Handler.failCause(cause)
        }
      }
    }

  /**
   * Creates a handler that responds with 403 - Forbidden status code
   */
  def forbidden: Handler[Any, Nothing, Any, Response] =
    error(Status.Forbidden)

  /**
   * Creates a handler that responds with 403 - Forbidden status code
   */
  def forbidden(message: => String): Handler[Any, Nothing, Any, Response] =
    error(Status.Forbidden, message)

  def from[H](handler: => H)(implicit h: ToHandler[H]): Handler[h.Env, h.Err, h.In, h.Out] =
    h.toHandler(handler)

  /**
   * Creates a handler which always responds the provided data and a 200 status
   * code
   */
  def fromBody(body: => Body): Handler[Any, Nothing, Any, Response] =
    response(Response(body = body))

  /**
   * Lifts an `Either` into a `Handler` alue.
   */
  def fromEither[Err, Out](either: => Either[Err, Out]): Handler[Any, Err, Any, Out] =
    either.fold(Handler.fail(_), Handler.succeed(_))

  def fromExit[Err, Out](exit: => Exit[Err, Out]): Handler[Any, Err, Any, Out] =
    new Handler[Any, Err, Any, Out] {
      override def apply(in: Any): ZIO[Any, Err, Out] = exit
    }

  /**
   * Creates a Handler from a pure function
   */
  def fromFunction[In]: FromFunction[In] = new FromFunction[In](())

  def fromFunctionHandler[In]: FromFunctionHandler[In] = new FromFunctionHandler[In](())

  /**
   * Creates a Handler from an pure function from A to HExit[R,E,B]
   */
  def fromFunctionExit[In]: FromFunctionExit[In] = new FromFunctionExit[In](())

  /**
   * Creates a Handler from an effectful pure function
   */
  def fromFunctionZIO[In]: FromFunctionZIO[In] = new FromFunctionZIO[In](())

  private def determineMediaType(filePath: String): Option[MediaType] = {
    filePath.lastIndexOf(".") match {
      case -1 => None
      case i  =>
        // Extract file extension
        val ext = filePath.substring(i + 1)
        MediaType.forFileExtension(ext)
    }
  }

  def fromFile[R](makeFile: => File)(implicit trace: Trace): Handler[R, Throwable, Any, Response] =
    fromFileZIO(ZIO.attempt(makeFile))

  def fromFileZIO[R](getFile: ZIO[R, Throwable, File])(implicit trace: Trace): Handler[R, Throwable, Any, Response] = {
    Handler.fromZIO[R, Throwable, Response](
      getFile.flatMap { file =>
        ZIO.suspend {
          if (!file.exists()) {
            ZIO.fail(new FileNotFoundException())
          } else if (file.isFile && !file.canRead) {
            ZIO.fail(new AccessDeniedException(file.getAbsolutePath))
          } else {
            if (file.isFile) {
              val length   = Headers(Header.ContentLength(file.length()))
              val response = http.Response(headers = length, body = Body.fromFile(file))
              val pathName = file.toPath.toString

              // Set MIME type in the response headers. This is only relevant in
              // case of RandomAccessFile transfers as browsers use the MIME type,
              // not the file extension, to determine how to process a URL.
              // {{{<a href="MSDN Doc">https://developer.mozilla.org/en-US/docs/Web/HTTP/Headers/Content-Type</a>}}}
              determineMediaType(pathName) match {
                case Some(mediaType) => ZIO.succeed(response.addHeader(Header.ContentType(mediaType)))
                case None            => ZIO.succeed(response)
              }
            } else {
              ZIO.fail(new NotDirectoryException(s"Found directory instead of a file."))
            }
          }
        }
      },
    )
  }

  /**
   * Creates a handler from a resource path
   */
  def fromResource(path: String)(implicit trace: Trace): Handler[Any, Throwable, Any, Response] =
    Handler.fromZIO {
      ZIO
        .attemptBlocking(getClass.getClassLoader.getResource(path))
        .map { resource =>
          if (resource == null) Handler.fail(new FileNotFoundException(s"Resource $path not found"))
          else fromResourceWithURL(resource)
        }
    }.flatten

  private[zio] def fromResourceWithURL(
    url: java.net.URL,
  )(implicit trace: Trace): Handler[Any, Throwable, Any, Response] = {
    url.getProtocol match {
      case "file" => Handler.fromFile(new File(url.getPath))
      case "jar"  =>
        val path         = new java.net.URI(url.getPath).getPath // remove "file:" prefix and normalize whitespace
        val bangIndex    = path.indexOf('!')
        val filePath     = path.substring(0, bangIndex)
        val resourcePath = path.substring(bangIndex + 2)
        val mediaType    = determineMediaType(resourcePath)
        val openZip      = ZIO.attemptBlockingIO(new ZipFile(filePath))
        val closeZip     = (jar: ZipFile) => ZIO.attemptBlocking(jar.close()).ignoreLogged

        def fileNotFound = new FileNotFoundException(s"Resource $resourcePath not found")

        def isDirectory = new IllegalArgumentException(s"Resource $resourcePath is a directory")

        Handler.fromZIO {
          ZIO
            .acquireReleaseWith(openZip)(closeZip) { jar =>
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
                response
                  .addHeader(Header.ContentType(t))
                  .addHeader(Header.ContentLength(contentLength))
              }
            }
        }

      case proto =>
        Handler.fail(new IllegalArgumentException(s"Unsupported protocol: $proto"))
    }
  }

  /**
   * Creates a Handler that always succeeds with a 200 status code and the
   * provided ZStream as the body
   */
  def fromStream[R](stream: ZStream[R, Throwable, String], charset: Charset = Charsets.Http)(implicit
    trace: Trace,
  ): Handler[R, Throwable, Any, Response] =
    Handler.fromZIO {
      ZIO.environment[R].map { env =>
        fromBody(Body.fromCharSequenceStream(stream.provideEnvironment(env), charset))
      }
    }.flatten

  /**
   * Creates a Handler that always succeeds with a 200 status code and the
   * provided ZStream as the body
   */
  def fromStream[R](stream: ZStream[R, Throwable, Byte])(implicit
    trace: Trace,
  ): Handler[R, Throwable, Any, Response] =
    Handler.fromZIO {
      ZIO.environment[R].map { env =>
        fromBody(Body.fromStream(stream.provideEnvironment(env)))
      }
    }.flatten

  /**
   * Converts a ZIO to a Handler type
   */
  def fromZIO[R, Err, Out](zio: => ZIO[R, Err, Out]): Handler[R, Err, Any, Out] =
    new Handler[R, Err, Any, Out] {
      override def apply(in: Any): ZIO[R, Err, Out] = zio
    }

  /**
   * Attempts to retrieve files from the classpath.
   */
  def getResource(path: String)(implicit trace: Trace): Handler[Any, Throwable, Any, java.net.URL] =
    Handler
      .fromZIO(ZIO.attemptBlocking(getClass.getClassLoader.getResource(path)))
      .flatMap { resource =>
        if (resource == null) Handler.fail(new IllegalArgumentException(s"Resource $path not found"))
        else Handler.succeed(resource)
      }

  /**
   * Attempts to retrieve files from the classpath.
   */
  def getResourceAsFile(path: String)(implicit trace: Trace): Handler[Any, Throwable, Any, File] =
    getResource(path).map(url => new File(url.getPath))

  /**
   * Creates a handler which always responds with the provided Html page.
   */
  def html(view: => Html): Handler[Any, Nothing, Any, Response] =
    response(Response.html(view))

  /**
   * Creates a pass thru Handler instance
   */
  def identity[A]: Handler[Any, Nothing, A, A] =
    new Handler[Any, Nothing, A, A] {
      override def apply(in: A): ZIO[Any, Nothing, A] = Exit.succeed(in)
    }

  def internalServerError: Handler[Any, Nothing, Any, Response] =
    error(Status.InternalServerError)

  def internalServerError(message: => String): Handler[Any, Nothing, Any, Response] =
    error(Status.InternalServerError, message)

  /**
   * Creates a handler which always responds with a 405 status code.
   */
  def methodNotAllowed: Handler[Any, Nothing, Any, Response] =
    error(Status.MethodNotAllowed)

  /**
   * Creates a handler which always responds with a 405 status code.
   */
  def methodNotAllowed(message: => String): Handler[Any, Nothing, Any, Response] =
    error(Status.MethodNotAllowed, message)

  /**
   * Creates a handler that fails with a NotFound exception.
   */
  def notFound: Handler[Any, Nothing, Request, Response] =
    Handler
      .fromFunctionHandler[Request] { request =>
        error(Status.NotFound, request.url.path.encode)
      }

  def notFound(message: => String): Handler[Any, Nothing, Any, Response] =
    error(Status.NotFound, message)

  /**
   * Creates a handler which always responds with a 200 status code.
   */
  def ok: Handler[Any, Nothing, Any, Response] =
    status(Status.Ok)

  /**
   * Creates a builder that can be used to create a handler that projects some
   * component from its input. This is useful when created nested or monadic
   * handlers, which require the input to all handlers be unified. By created
   * extractors, the "smaller" handlers can extract what they need from the
   * input to the "biggest" handler.
   */
  def param[A]: ParamExtractorBuilder[A] =
    new ParamExtractorBuilder[A](())

  /**
   * Creates a handler which always responds with the same value.
   */
  def response(response: => Response): Handler[Any, Nothing, Any, Response] =
    succeed(response)

  /**
   * Converts a ZIO to a handler type
   */
  def responseZIO[R, Err](getResponse: ZIO[R, Err, Response]): Handler[R, Err, Any, Response] =
    fromZIO(getResponse)

  def stackTrace(implicit trace: Trace): Handler[Any, Nothing, Any, StackTrace] =
    fromZIO(ZIO.stackTrace)

  /**
   * Creates a handler which always responds with the same status code and empty
   * data.
   */
  def status(code: => Status): Handler[Any, Nothing, Any, Response] =
    succeed(Response(code))

  /**
   * Creates a Handler that always returns the same response and never fails.
   */
  def succeed[Out](out: => Out): Handler[Any, Nothing, Any, Out] =
    fromExit(Exit.succeed(out))

  /**
   * Creates a handler which responds with an Html page using the built-in
   * template.
   */
  def template(heading: => CharSequence)(view: Html): Handler[Any, Nothing, Any, Response] =
    response(Response.html(Template.container(heading)(view)))

  /**
   * Creates a handler which always responds with the same plain text.
   */
  def text(text: => CharSequence): Handler[Any, Nothing, Any, Response] =
    response(Response.text(text))

  /**
   * Creates a handler that responds with a 408 status code after the provided
   * time duration
   */
  def timeout(duration: Duration)(implicit trace: Trace): Handler[Any, Nothing, Any, Response] =
    status(Status.RequestTimeout).delay(duration)

  /**
   * Creates a handler which always responds with a 413 status code.
   */
  def tooLarge: Handler[Any, Nothing, Any, Response] =
    Handler.status(Status.RequestEntityTooLarge)

  val unit: Handler[Any, Nothing, Any, Unit] =
    fromExit(Exit.unit)

  /**
   * Constructs a handler from a function that uses a web socket.
   */
  final def webSocket[Env](
    f: WebSocketChannel => ZIO[Env, Throwable, Any],
  ): WebSocketApp[Env] =
    WebSocketApp(Handler.fromFunctionZIO(f))

  final implicit class RequestHandlerSyntax[-R, +Err](val self: RequestHandler[R, Err])
      extends HeaderModifier[RequestHandler[R, Err]] {

    /**
     * Patches the response produced by the handler
     */
    def patch(patch: Response.Patch)(implicit trace: Trace): RequestHandler[R, Err] = self.map(patch(_))

    /**
     * Overwrites the method in the incoming request
     */
    def method(method: Method): RequestHandler[R, Err] =
      self.contramap[Request](_.copy(method = method))

    /**
     * Overwrites the path in the incoming request
     */
    def path(path: Path): RequestHandler[R, Err] = self.contramap[Request](_.path(path))

    /**
     * Sets the status in the response produced by the handler
     */
    def status(status: Status)(implicit trace: Trace): RequestHandler[R, Err] = patch(
      Response.Patch.status(status),
    )

    /**
     * Overwrites the url in the incoming request
     */
    def url(url: URL): RequestHandler[R, Err] = self.contramap[Request](_.copy(url = url))

    /**
     * Updates the current Headers with new one, using the provided update
     * function passed.
     */
    override def updateHeaders(update: Headers => Headers)(implicit trace: Trace): RequestHandler[R, Err] =
      self.map(_.updateHeaders(update))
  }

  final implicit class ResponseOutputSyntax[-R, +Err, -In](val self: Handler[R, Err, In, Response]) extends AnyVal {

    /**
     * Extracts body
     */
    def body(implicit trace: Trace): Handler[R, Err, In, Body] =
      self.map(_.body)

    /**
     * Extracts content-length from the response if available
     */
    def contentLength(implicit trace: Trace): Handler[R, Err, In, Option[Header.ContentLength]] =
      self.map(_.header(Header.ContentLength))

    /**
     * Extracts the value of ContentType header
     */
    def contentType(implicit trace: Trace): Handler[R, Err, In, Option[Header.ContentType]] =
      header(Header.ContentType)

    /**
     * Extracts the `Headers` from the type `B` if possible
     */
    def headers(implicit trace: Trace): Handler[R, Err, In, Headers] =
      self.map(_.headers)

    /**
     * Extracts the value of the provided header name.
     */
    def header(headerType: HeaderType)(implicit
      trace: Trace,
    ): Handler[R, Err, In, Option[headerType.HeaderValue]] =
      self.map(_.header(headerType))

    def headerOrFail(
      headerType: HeaderType,
    )(implicit trace: Trace, ev: Err <:< String): Handler[R, String, In, Option[headerType.HeaderValue]] =
      self
        .mapError(ev)
        .flatMap { response =>
          response.headerOrFail(headerType) match {
            case Some(Left(error))  => Handler.fail(error)
            case Some(Right(value)) => Handler.succeed(Some(value))
            case None               => Handler.succeed(None)
          }
        }

    def rawHeader(name: CharSequence)(implicit trace: Trace): Handler[R, Err, In, Option[String]] =
      self.map(_.rawHeader(name))

    /**
     * Extracts `Status` from the type `B` is possible.
     */
    def status(implicit trace: Trace): Handler[R, Err, In, Status] =
      self.map(_.status)
  }

  final class ContraFlatMap[-R, +Err, -In, +Out, In1](val self: Handler[R, Err, In, Out]) extends AnyVal {
    def apply[R1 <: R, Err1 >: Err](f: In1 => Handler[R1, Err1, Any, In])(implicit
      trace: Trace,
    ): Handler[R1, Err1, In1, Out] =
      fromFunctionHandler(f) >>> self
  }

  final class FromFunction[In](val self: Unit) extends AnyVal {
    def apply[Out](f: In => Out): Handler[Any, Nothing, In, Out] =
      new Handler[Any, Nothing, In, Out] {
        override def apply(in: In): ZIO[Any, Nothing, Out] =
          try {
            Exit.succeed(f(in))
          } catch {
            case error: Throwable => Exit.die(error)
          }
      }
  }

  final class FromFunctionHandler[In](val self: Unit) extends AnyVal {
    def apply[R, Err, Out](f: In => Handler[R, Err, In, Out]): Handler[R, Err, In, Out] =
      new Handler[R, Err, In, Out] {
        override def apply(in: In): ZIO[R, Err, Out] =
          f(in)(in)
      }
  }

  final class FromFunctionExit[In](val self: Unit) extends AnyVal {
    def apply[R, Err, Out](f: In => Exit[Err, Out]): Handler[Any, Err, In, Out] =
      new Handler[Any, Err, In, Out] {
        override def apply(in: In): ZIO[Any, Err, Out] =
          try {
            f(in)
          } catch {
            case error: Throwable => Exit.die(error)
          }
      }
  }

  final class FromFunctionZIO[In](val self: Unit) extends AnyVal {
    def apply[R, Err, Out](f: In => ZIO[R, Err, Out]): Handler[R, Err, In, Out] =
      new Handler[R, Err, In, Out] {
        override def apply(in: In): ZIO[R, Err, Out] =
          f(in)
      }
  }

  final class ParamExtractorBuilder[A](val unit: Unit) extends AnyVal {
    def apply[B](project: A => B): Handler[Any, Nothing, A, B] =
      Handler.identity[B].contramap[A](project)
  }
}
