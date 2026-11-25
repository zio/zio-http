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

import java.io.{File, FileNotFoundException}
import java.nio.charset.Charset
import java.nio.file.{AccessDeniedException, NotDirectoryException}

import scala.reflect.ClassTag
import scala.util.control.NonFatal

import zio._

import zio.stream.ZStream

import zio.http.Handler.ApplyContextAspect
import zio.http.Header.HeaderType
import zio.http.internal.{HeaderGetters, HeaderModifier}
import zio.http.template._

/**
 * A `Handler[R, Err, In, Out]` is a function from an input of type `In` to an
 * effect that needs an environment `R` and a `Scope`, and fails with `Err` or
 * succeeds with `Out`. A handler that takes a [[Request]] and returns a
 * [[Response]] answers HTTP requests: give it to a [[RoutePattern]] with `->`
 * to build a [[Route]], or turn it into [[Routes]] with `toRoutes`.
 *
 * Build handlers with the constructors of the companion object
 * (`Handler.fromFunction`, `Handler.fromZIO`, `Handler.ok`, `Handler.text`,
 * ...) or with the `handler` function of the `zio.http` package, and change
 * them with `map`, `contramap`, `flatMap`, `catchAll` and the other operators.
 * Constructors such as `succeed`, `fromZIO` and `fromResponse` evaluate their
 * by-name argument each time the handler runs.
 *
 * {{{
 * import zio._
 *
 * val greet: Handler[Any, Nothing, Request, Response] =
 *   Handler.fromFunction[Request] { request =>
 *     Response.text("Hello from " + request.url.path.encode)
 *   }
 *
 * val response: ZIO[Scope, Nothing, Response] =
 *   greet(Request.get(URL(Path("/hello"))))
 * }}}
 *
 * @see
 *   [[https://zio.dev/zio-http/reference/handler/ Request Handler]]
 */
sealed trait Handler[-R, +Err, -In, +Out] { self =>

  /**
   * Applies a [[HandlerAspect]] that provides no context (`Unit`) to this
   * request handler. The aspect runs its incoming transformations before this
   * handler and its outgoing transformations on the response or error response.
   */
  def @@[Env1 <: R, In1 <: In](aspect: HandlerAspect[Env1, Unit])(implicit
    in: Handler.IsRequest[In1],
    out: Out <:< Response,
    err: Err <:< Response,
  ): Handler[Env1, Response, Request, Response] = {
    def convert(handler: Handler[R, Err, In, Out]): Handler[R, Response, Request, Response] =
      handler.asInstanceOf[Handler[R, Response, Request, Response]]

    aspect.applyHandler(convert(self))
  }

  /**
   * Applies a [[HandlerAspect]] that produces a context of type `Ctx`, and
   * provides that context to this handler as a service of its environment. The
   * result no longer needs `Ctx`.
   */
  def @@[Env0, Ctx <: R, In1 <: In](aspect: HandlerAspect[Env0, Ctx])(implicit
    in: Handler.IsRequest[In1],
    out: Out <:< Response,
    err: Err <:< Response,
    trace: Trace,
    tag: Tag[Ctx],
  ): Handler[Env0, Response, Request, Response] =
    aspect.applyHandlerContext {
      Handler.scoped[Env0] {
        handler { (ctx: Ctx, req: Request) =>
          val handler: ZIO[Scope & Ctx, Response, Response] =
            self
              .asInstanceOf[Handler[Ctx, Response, Request, Response]](req)
          handler.provideSomeEnvironment[Scope & Env0](_.add[Ctx](ctx))
        }
      }
    }

  /**
   * Starts applying a context-providing [[HandlerAspect]] while keeping `Env0`
   * as the environment that is still required: `handler.@@[Env0](aspect)`
   * provides the aspect's context and leaves `Env0` for later.
   */
  def @@[Env0]: ApplyContextAspect[R, Err, In, Out, Env0] =
    new ApplyContextAspect(self)

  /**
   * Alias for `flatMap`.
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
   * Runs this handler and, if it fails with an error, runs `that` handler on
   * the same input instead. A defect of this handler is not recovered: it stays
   * a defect. Alias for `orElse`.
   */
  final def <>[R1 <: R, Err1, In1 <: In, Out1 >: Out](
    that: Handler[R1, Err1, In1, Out1],
  )(implicit trace: Trace): Handler[R1, Err1, In1, Out1] =
    self.orElse(that)

  /**
   * Alias for `zip`.
   */
  final def <*>[R1 <: R, Err1 >: Err, In1 <: In, Out1](
    that: Handler[R1, Err1, In1, Out1],
  )(implicit trace: Trace): Handler[R1, Err1, In1, (Out, Out1)] =
    self.zip(that)

  /**
   * Alias for `zipLeft`.
   */
  final def <*[R1 <: R, Err1 >: Err, In1 <: In, Out1](
    that: Handler[R1, Err1, In1, Out1],
  )(implicit trace: Trace): Handler[R1, Err1, In1, Out] =
    self.zipLeft(that)

  /**
   * Alias for `zipRight`.
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
      override def apply(in: In): ZIO[Scope & R1, Err1, Out1] =
        self(in).flatMap(that(_))
    }

  /**
   * Consumes the input and executes the Handler.
   */
  def apply(in: In): ZIO[Scope & R, Err, Out]

  /**
   * Returns a handler that succeeds with `out` whenever this handler succeeds.
   */
  final def as[Out1](out: Out1)(implicit trace: Trace): Handler[R, Err, In, Out1] =
    self.map(_ => out)

  /**
   * Changes the environment type to `R2`, given evidence that `R2` is a subtype
   * of `R`. The handler is not changed.
   */
  final def asEnvType[R2](implicit ev: R2 <:< R): Handler[R2, Err, In, Out] =
    self.asInstanceOf[Handler[R2, Err, In, Out]]

  /**
   * Changes the error type to `Err2`, given evidence that `Err` is a subtype of
   * `Err2`. The handler is not changed.
   */
  final def asErrorType[Err2](implicit ev: Err <:< Err2): Handler[R, Err2, In, Out] =
    self.asInstanceOf[Handler[R, Err2, In, Out]]

  /**
   * Changes the input type to `In2`, given evidence that `In2` is a subtype of
   * `In`. The handler is not changed.
   */
  final def asInType[In2](implicit ev: In2 <:< In): Handler[R, Err, In2, Out] =
    self.asInstanceOf[Handler[R, Err, In2, Out]]

  /**
   * Changes the output type to `Out2`, given evidence that `Out` is a subtype
   * of `Out2`. The handler is not changed.
   */
  final def asOutType[Out2](implicit ev: Out <:< Out2): Handler[R, Err, In, Out2] =
    self.asInstanceOf[Handler[R, Err, In, Out2]]

  /**
   * Returns a handler that succeeds with the body of the response that this
   * handler produces.
   */
  final def body(implicit ev: Out <:< Response, trace: Trace): Handler[R, Err, In, Body] =
    self.map(_.body)

  /**
   * Recovers from every error of this handler by running the handler that `f`
   * returns for the error, on the same input. Defects and interruption are not
   * recovered.
   */
  final def catchAll[R1 <: R, Err1, In1 <: In, Out1 >: Out](f: Err => Handler[R1, Err1, In1, Out1])(implicit
    trace: Trace,
  ): Handler[R1, Err1, In1, Out1] =
    self.foldHandler(f, Handler.succeed(_))

  /**
   * Recovers from every failure of this handler, including defects and
   * interruption, by running the handler that `f` returns for the `Cause`, on
   * the same input.
   */
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
   * Returns a handler that transforms its input with `f` before passing it to
   * this handler.
   */
  final def contramap[In1](f: In1 => In): Handler[R, Err, In1, Out] =
    new Handler[R, Err, In1, Out] {
      override def apply(in: In1): ZIO[Scope & R, Err, Out] =
        self(f(in))
    }

  /**
   * Returns a handler that transforms its input with the effect `f` before
   * passing it to this handler. A failure of `f` is a failure of the handler.
   */
  final def contramapZIO[R1 <: R, Err1 >: Err, In1](f: In1 => ZIO[R1, Err1, In])(implicit
    trace: Trace,
  ): Handler[R1, Err1, In1, Out] =
    new Handler[R1, Err1, In1, Out] {
      override def apply(in: In1): ZIO[Scope & R1, Err1, Out] =
        f(in).flatMap(self(_))
    }

  /**
   * Returns a builder that takes a function `f: In1 => Handler[..., Any, In]`:
   * the resulting handler runs the handler that `f` returns for its input, and
   * passes that handler's output to this handler.
   */
  final def contraFlatMap[In1]: Handler.ContraFlatMap[R, Err, In, Out, In1] =
    new Handler.ContraFlatMap(self)

  /**
   * When this handler succeeds, waits `duration` before producing its output.
   * Failures are not delayed. Alias for `delayAfter`.
   */
  final def delay(duration: Duration)(implicit trace: Trace): Handler[R, Err, In, Out] =
    self.delayAfter(duration)

  /**
   * When this handler succeeds, waits `duration` before producing its output.
   * Failures are not delayed.
   */
  final def delayAfter(duration: Duration)(implicit trace: Trace): Handler[R, Err, In, Out] =
    self.mapZIO(out => ZIO.succeed(out).delay(duration))

  /**
   * Waits for `duration` before passing the input to this handler.
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
   * Returns a handler that runs this handler, then runs the handler that `f`
   * returns for its output, on the same input.
   */
  final def flatMap[R1 <: R, Err1 >: Err, In1 <: In, Out1](
    f: Out => Handler[R1, Err1, In1, Out1],
  )(implicit trace: Trace): Handler[R1, Err1, In1, Out1] =
    self.foldHandler(
      Handler.fail(_),
      f(_),
    )

  /**
   * Returns a handler that runs `onFailure` with the full `Cause` when this
   * handler fails (including defects and interruption), or `onSuccess` with the
   * output when it succeeds. The chosen handler runs on the same input.
   */
  final def foldCauseHandler[R1 <: R, Err1, In1 <: In, Out1](
    onFailure: Cause[Err] => Handler[R1, Err1, In1, Out1],
    onSuccess: Out => Handler[R1, Err1, In1, Out1],
  )(implicit trace: Trace): Handler[R1, Err1, In1, Out1] =
    new Handler[R1, Err1, In1, Out1] {
      override def apply(in: In1): ZIO[Scope & R1, Err1, Out1] =
        self(in).foldCauseZIO(
          cause => onFailure(cause)(in),
          out => onSuccess(out)(in),
        )
    }

  /**
   * Returns a handler that runs `onFailure` with the error when this handler
   * fails with an error, or `onSuccess` with the output when it succeeds. The
   * chosen handler runs on the same input. Defects and interruption are passed
   * on unchanged.
   */
  final def foldHandler[R1 <: R, Err1, In1 <: In, Out1](
    onFailure: Err => Handler[R1, Err1, In1, Out1],
    onSuccess: Out => Handler[R1, Err1, In1, Out1],
  )(implicit trace: Trace): Handler[R1, Err1, In1, Out1] =
    self.foldCauseHandler(
      cause => cause.failureOrCause.fold(onFailure, Handler.failCause(_)),
      onSuccess,
    )

  /**
   * Returns a handler that succeeds with the headers of the response that this
   * handler produces.
   */
  final def headers(implicit ev: Out <:< Response, trace: Trace): Handler[R, Err, In, Headers] =
    self.map(_.headers)

  /**
   * Returns a handler that succeeds with the header of type `headerType` from
   * the response that this handler produces, or `None` when the response has no
   * such header or it does not parse.
   */
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
   * Handles an error of this handler with the effect `f`: when `f` succeeds,
   * its value becomes the output of the handler, and when `f` fails, its error
   * becomes the new error.
   */
  final def mapErrorZIO[R1 <: R, Err1, Out1 >: Out](f: Err => ZIO[R1, Err1, Out1])(implicit
    trace: Trace,
  ): Handler[R1, Err1, In, Out1] =
    self.foldHandler(err => Handler.fromZIO(f(err)), Handler.succeed(_))

  /**
   * Handles every failure of this handler except pure interruption with the
   * effect `f`, which receives the full `Cause`: when `f` succeeds, its value
   * becomes the output of the handler, and when `f` fails, its error becomes
   * the new error.
   */
  final def mapErrorCauseZIO[R1 <: R, Err1, Out1 >: Out](
    f: Cause[Err] => ZIO[R1, Err1, Out1],
  )(implicit trace: Trace): Handler[R1, Err1, In, Out1] =
    self.foldCauseHandler(
      err =>
        if (err.isInterruptedOnly) Handler.failCause(err.asInstanceOf[Cause[Nothing]]) else Handler.fromZIO(f(err)),
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

  /**
   * Returns a handler that runs `f` with the `Exit` of this handler, whether it
   * succeeds or fails, and then returns the original result. A failure of `f`
   * becomes the failure of the handler.
   */
  final def onExit[R1 <: R, Err1 >: Err](f: Exit[Err, Out] => ZIO[R1, Err1, Any])(implicit
    trace: Trace,
  ): Handler[R1, Err1, In, Out] =
    self.tapAllZIO(
      cause => f(Exit.failCause(cause)),
      out => f(Exit.succeed(out)),
    )

  /**
   * Returns a handler that succeeds with `Some` of the output, or with `None`
   * when this handler fails with an error. Defects are not caught.
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
   * Turns every error of this handler into a defect, using `f` to convert the
   * error into a `Throwable`.
   */
  final def orDieWith(f: Err => Throwable)(implicit ev: CanFail[Err], trace: Trace): Handler[R, Nothing, In, Out] =
    self.foldHandler(err => Handler.die(f(err)), Handler.succeed(_))

  /**
   * Runs this handler and, if it fails with an error, runs `that` handler on
   * the same input instead. A defect of this handler is not recovered: it stays
   * a defect.
   */
  final def orElse[R1 <: R, Err1, In1 <: In, Out1 >: Out](
    that: Handler[R1, Err1, In1, Out1],
  )(implicit trace: Trace): Handler[R1, Err1, In1, Out1] =
    new Handler[R1, Err1, In1, Out1] {
      override def apply(in: In1): ZIO[Scope & R1, Err1, Out1] = {
        val s = self(in)
        s match {
          case s @ Exit.Success(_)                    => s
          case Exit.Failure(cause) if cause.isDie     => Exit.die(cause.dieOption.get)
          case Exit.Failure(cause) if cause.isFailure => that(in)
          case _                                      => s.orElse(that(in))
        }
      }
    }

  /**
   * Provides the environment to Handler.
   */
  final def provideEnvironment(r: ZEnvironment[R])(implicit trace: Trace): Handler[Any, Err, In, Out] =
    new Handler[Any, Err, In, Out] {
      override def apply(in: In): ZIO[Scope, Err, Out] =
        self(in).asInstanceOf[ZIO[Scope & R, Err, Out]].provideSomeEnvironment[Scope](_.unionAll[R](r))
    }

  /**
   * Provides layer to Handler.
   */
  final def provideLayer[Err1 >: Err, R0](layer: ZLayer[R0, Err1, R])(implicit
    trace: Trace,
  ): Handler[R0, Err1, In, Out] =
    new Handler[R0, Err1, In, Out] {
      override def apply(in: In): ZIO[Scope & R0, Err1, Out] =
        ZIO.scopedWith(layerScope =>
          layer
            .build(layerScope)
            .flatMap(r =>
              self(in).asInstanceOf[ZIO[Scope & R, Err, Out]].provideSomeEnvironment[Scope](_.unionAll[R](r)),
            ),
        )
    }

  /**
   * Provides some of the environment to Handler.
   */
  final def provideSomeEnvironment[R1](f: ZEnvironment[R1] => ZEnvironment[R])(implicit
    trace: Trace,
  ): Handler[R1, Err, In, Out] =
    new Handler[R1, Err, In, Out] {
      override def apply(in: In): ZIO[Scope & R1, Err, Out] =
        self(in).asInstanceOf[ZIO[R, Err, Out]].provideSomeEnvironment(f)
    }

  /**
   * Provides some of the environment to Handler leaving the remainder `R0`.
   */
  final def provideSomeLayer[R0, R1: Tag, Err1 >: Err](
    layer: ZLayer[R0, Err1, R1],
  )(implicit ev: R0 with R1 <:< R, trace: Trace): Handler[R0, Err1, In, Out] =
    new Handler[R0, Err1, In, Out] {
      override def apply(in: In): ZIO[Scope & R0, Err1, Out] =
        ZIO.scopedWith(layerScope =>
          (ZLayer.environment[R0] <*> layer)
            .build(layerScope)
            .flatMap(r =>
              self(in)
                .asInstanceOf[ZIO[Scope & R, Err, Out]]
                .provideSomeEnvironment[Scope](_.unionAll[R0 with R1](r).asInstanceOf[ZEnvironment[Scope & R]]),
            ),
        )
    }

  /**
   * Runs this handler and `that` handler on the same input and returns the
   * result of the first one to complete, whether it succeeds or fails. A
   * handler that has already completed when called (such as `Handler.succeed`
   * or `Handler.fail`) wins without a race.
   */
  final def race[R1 <: R, Err1 >: Err, In1 <: In, Out1 >: Out](
    that: Handler[R1, Err1, In1, Out1],
  )(implicit trace: Trace): Handler[R1, Err1, In1, Out1] =
    new Handler[R1, Err1, In1, Out1] {
      override def apply(in: In1): ZIO[Scope & R1, Err1, Out1] =
        (self(in), that(in)) match {
          // The type arguments are erased, so they are left as wildcards: an already-completed
          // effect is returned as-is, and it is the static type that guarantees it fits.
          case (self: Exit[_, _], _)  => self
          case (_, other: Exit[_, _]) => other
          case (self, other)          => self.raceFirst(other)
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
   * Keeps the errors for which `pf` is defined, mapped by `pf`, and turns the
   * rest into defects, using `f` to convert them into a `Throwable`.
   */
  final def refineOrDieWith[Err1](
    pf: PartialFunction[Err, Err1],
  )(f: Err => Throwable)(implicit ev: CanFail[Err], trace: Trace): Handler[R, Err1, In, Out] =
    self.foldHandler(
      err => pf.andThen(Handler.fail(_)).applyOrElse(err, (e: Err) => Handler.die(f(e))),
      Handler.succeed(_),
    )

  /**
   * Runs this request handler on a [[Request]] built from `method`, `path`,
   * `headers` and `body`. The request URL is relative (it has no scheme or
   * host).
   */
  final def run(
    method: Method = Method.GET,
    path: Path = Path.root,
    headers: Headers = Headers.empty,
    body: Body = Body.empty,
  )(implicit ev: Request <:< In): ZIO[Scope & R, Err, Out] =
    self(ev(Request(method = method, url = URL.root.path(path), headers = headers, body = body)))

  /**
   * Runs this handler on `in`. Same as `apply`.
   */
  final def runZIO(in: In): ZIO[Scope & R, Err, Out] =
    self(in)

  /**
   * Converts every failure of this handler except pure interruption, including
   * defects, into a [[Response]] in the error channel, using
   * `Response.fromCause` and the current [[ErrorResponseConfig]]. A failure
   * that is not already a `Response` is logged at the error level.
   */
  final def sandbox(implicit trace: Trace): Handler[R, Response, In, Out] =
    self.mapErrorCauseZIO { cause =>
      logUnhandledError(cause) *>
        ErrorResponseConfig.configRef.getWith(cfg => Exit.fail(Response.fromCause(cause, cfg)))
    }

  /**
   * Log an unhandled error unless the cause is already a response
   */
  private def logUnhandledError(cause: Cause[Any]): UIO[Unit] =
    cause.failureOrCause match {
      case Left(_: Response) => ZIO.unit
      case _                 => ZIO.logErrorCause("Unhandled exception in request handler", cause)
    }

  /**
   * Returns a handler that succeeds with the status of the response that this
   * handler produces.
   */
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
      override def apply(in: In): ZIO[Scope & R1, Err1, Out] =
        self(in) match {
          case Exit.Success(a)     => onSuccess(a).as(a)
          case Exit.Failure(cause) => onFailure(cause) *> ZIO.failCause(cause)
          case z                   => z.tapErrorCause(onFailure).tap(onSuccess)
        }
    }

  /**
   * Returns a handler that runs `f` with the `Cause` when this handler fails,
   * then fails with the same cause. A failure of `f` replaces the original
   * failure.
   */
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

  /**
   * Returns a handler that succeeds with `Some` of the output, or with `None`
   * when this handler does not complete within `duration`. A handler that times
   * out is interrupted.
   */
  def timeout(duration: Duration)(implicit trace: Trace): Handler[R, Err, In, Option[Out]] =
    Handler.scoped[R] {
      Handler
        .fromFunctionZIO[In] { request =>
          self(request).timeout(duration)
        }
    }

  /**
   * Returns a handler that succeeds with `out` when this handler does not
   * complete within `duration`. A handler that times out is interrupted.
   */
  def timeoutFail[Out1 >: Out](out: Out1)(duration: Duration)(implicit trace: Trace): Handler[R, Err, In, Out1] =
    Handler.scoped[R] {
      Handler
        .fromFunctionZIO[In] { request =>
          self(request).timeout(duration).map(_.getOrElse(out))
        }
        .asInstanceOf[Handler[R, Err, In, Out1]]
    }

  /**
   * Converts this request handler into [[Routes]] with a single route that
   * matches every method and path. When the routes run, a defect of the handler
   * becomes a 500 response, through the same error handling as any other route.
   */
  def toRoutes(implicit in: Request <:< In, out: Out <:< Response, trace: Trace): Routes[R, Err] = {
    val handler: Handler[R, Err, Request, Response] =
      self.asInstanceOf[Handler[R, Err, Request, Response]]

    Routes.singleton(handler.contramap[(Path, Request)](_._2))
  }

  /**
   * Takes some defects and converts them into failures.
   */
  final def unrefine[Err1 >: Err](pf: PartialFunction[Throwable, Err1])(implicit
    trace: Trace,
  ): Handler[R, Err1, In, Out] =
    unrefineWith(pf)(err => err)

  /**
   * Converts defects whose class is `Err1` (or a subclass) into errors of type
   * `Err1`.
   */
  final def unrefineTo[Err1 >: Err: ClassTag](implicit trace: Trace): Handler[R, Err1, In, Out] = {
    val pf: PartialFunction[Throwable, Err1] = { case err: Err1 =>
      err
    }
    unrefine(pf)
  }

  /**
   * Converts the defects for which `pf` is defined into errors, and maps the
   * existing errors with `f`.
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
   * Returns a handler that runs the `ZIO` effect that this handler produces,
   * and succeeds with its result.
   */
  final def unwrapZIO[R1 <: R, Err1 >: Err, Out1](implicit
    ev: Out <:< ZIO[R1, Err1, Out1],
    trace: Trace,
  ): Handler[R1, Err1, In, Out1] =
    self.flatMap(out => Handler.fromZIO(ev(out)))

  /**
   * Changes the error type to `Err1` and the output type to `Out1`, given
   * evidence that they are supertypes of `Err` and `Out`. The handler is not
   * changed.
   */
  def widen[Err1, Out1](implicit ev1: Err <:< Err1, ev2: Out <:< Out1): Handler[R, Err1, In, Out1] =
    self.asInstanceOf[Handler[R, Err1, In, Out1]]

  /**
   * Returns a handler that runs this handler and then `that` handler on the
   * same input, and succeeds with both outputs as a tuple.
   */
  final def zip[R1 <: R, Err1 >: Err, In1 <: In, Out1](
    that: Handler[R1, Err1, In1, Out1],
  )(implicit trace: Trace): Handler[R1, Err1, In1, (Out, Out1)] =
    self.flatMap(out => that.map(out1 => (out, out1)))

  /**
   * Returns a handler that runs this handler and then `that` handler on the
   * same input, and succeeds with the output of this handler.
   */
  final def zipLeft[R1 <: R, Err1 >: Err, In1 <: In, Out1](
    that: Handler[R1, Err1, In1, Out1],
  )(implicit trace: Trace): Handler[R1, Err1, In1, Out] =
    self.flatMap(out => that.as(out))

  /**
   * Returns a handler that runs this handler and then `that` handler on the
   * same input, and succeeds with the output of `that` handler.
   */
  final def zipRight[R1 <: R, Err1 >: Err, In1 <: In, Out1](
    that: Handler[R1, Err1, In1, Out1],
  )(implicit trace: Trace): Handler[R1, Err1, In1, Out1] =
    self.flatMap(_ => that)
}

/**
 * Constructors for [[Handler]] values, from constant responses (`ok`, `text`,
 * `html`, `status`, `error`) to functions (`fromFunction`, `fromFunctionZIO`),
 * effects (`fromZIO`), files and streams.
 *
 * @see
 *   [[https://zio.dev/zio-http/reference/handler/ Request Handler]]
 */
object Handler extends HandlerPlatformSpecific with HandlerVersionSpecific {

  private val errorMediaTypes = List(MediaType.text.html, MediaType.application.json, MediaType.text.plain)

  /**
   * Evidence that the input type of a handler is [[Request]]. Only `Request`
   * has an instance. The `@@` operators of [[Handler]] require it.
   */
  sealed trait IsRequest[-A]

  /**
   * Holds the only instance of [[Handler.IsRequest]].
   */
  object IsRequest {

    /**
     * The instance for [[Request]].
     */
    implicit val request: IsRequest[Request] = new IsRequest[Request] {}
  }

  /**
   * Returns a handler that reads the body of `request` into a `Chunk[Byte]`. It
   * fails with an `Exception` whose message is "Too large input" once the bytes
   * read reach `limit`, so a body of exactly `limit` bytes fails too. The input
   * of the handler is ignored.
   */
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
   * Creates a handler that evaluates `out` each time it runs and succeeds with
   * the value, or fails with the exception when the evaluation throws a
   * non-fatal exception.
   */
  def attempt[Out](out: => Out): Handler[Any, Throwable, Any, Out] =
    fromExit {
      try Exit.succeed(out)
      catch {
        case error if NonFatal(error) => Exit.fail(error)
      }
    }

  /**
   * A handler that always responds with status 400 Bad Request and an empty
   * body.
   */
  val badRequest: Handler[Any, Nothing, Any, Response] =
    error(Status.BadRequest)

  /**
   * Creates a handler that always responds with status 400 Bad Request, like `error(Status.BadRequest, message)`: `message` is in the body only when the current [[ErrorResponseConfig]] has `withErrorBody` set, which by default is the case only in `Mode.Dev`.
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
   * Creates a handler that always responds with the error status `status` and
   * an empty body.
   */
  def error(status: => Status.Error): Handler[Any, Nothing, Any, Response] =
    fromResponse(Response.error(status))

  /**
   * Creates a handler with an error and the specified error message. The error
   * message will be returned as HTML, JSON or plain text depending on the
   * `Accept` header of the request, defaulting to
   * [[ErrorResponseConfig.errorFormat]]. If
   * [[ErrorResponseConfig.withErrorBody]] is `false`, the error message will
   * not be included in the response. If the error message should always be
   * included in the response, use `Handler.fromResponse(Response.error(status,
   * message))`
   */
  def error(status: => Status.Error, message: => String): Handler[Any, Nothing, Any, Response] =
    error(status, message, Routes.empty)

  /**
   * Creates a handler with an error and the specified error message. The error
   * message will be returned as HTML, JSON or plain text depending on the
   * `Accept` header of the request, defaulting to
   * [[ErrorResponseConfig.errorFormat]]. If
   * [[ErrorResponseConfig.withErrorBody]] is `false`, the error message will
   * not be included in the response. If the error message should always be
   * included in the response, use
   * `Handler.fromResponse(Response.error(status, message))`
   *
   * In `Mode.Dev`, when the message is included and the chosen format is HTML,
   * a 404 Not Found response lists the route patterns of `routes` in an HTML
   * page instead, unless `routes` is empty.
   */
  def error(status: => Status.Error, message: => String, routes: Routes[_, _]): Handler[Any, Nothing, Any, Response] =
    (fromResponse(Response.status(status)) @@ Middleware.interceptHandlerStateful(
      handler((req: Request) => (req.header(Header.Accept), (req, ()))),
    ) {
      handler { (accept: Option[Header.Accept], res: Response) =>
        ErrorResponseConfig.configRef.get.map { cfg =>
          if (cfg.withErrorBody) {
            val mediaType: MediaType = accept
              .flatMap(_.mimeTypes.sorted.map(_.mediaType).collectFirst {
                case mt if errorMediaTypes.exists(mt.matches(_, ignoreParameters = true)) =>
                  errorMediaTypes.find(mt.matches(_, ignoreParameters = true)).get
              })
              .getOrElse(cfg.errorFormat.mediaType)
            mediaType match {
              case MediaType.application.`json`                                                               =>
                Response(status = status, body = Body.fromString(s"""{"status": "$status", "error": "$message"}"""))
                  .contentType(MediaType.application.json)
              case MediaType.text.`html` if Mode.isDev && status == Status.NotFound && routes.routes.nonEmpty =>
                Response.html(RoutesOverview(routes.routes.map(_.routePattern)), Status.NotFound)
              case MediaType.text.`html`                                                                      =>
                Response(
                  status = status,
                  body = Body.fromString(
                    s"""<!DOCTYPE html><html><head><title>$status</title></head><body><h1>$status</h1><p>$message</p></body></html>""",
                  ),
                ).contentType(MediaType.text.html)
              case MediaType.text.`plain`                                                                     =>
                Response(status = status, body = Body.fromString(message)).contentType(MediaType.text.plain)
              case _ => throw new Exception("Unsupported media type")
            }
          } else {
            res
          }
        }
      }
    }).asInstanceOf[Handler[Any, Nothing, Any, Response]]

  /**
   * Creates a handler that always fails with `err`.
   */
  def fail[Err](err: => Err): Handler[Any, Err, Any, Nothing] =
    fromExit(Exit.fail(err))

  /**
   * Creates a handler that always fails with `cause`.
   */
  def failCause[Err](cause: => Cause[Err]): Handler[Any, Err, Any, Nothing] =
    fromExit(Exit.failCause(cause))

  /**
   * Creates a handler that runs `handlers` one after another on the same input
   * until one succeeds. A failure for which `isRecoverable` is false stops the
   * search and becomes the failure of the handler. By default, every failure
   * except a defect is recoverable.
   */
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
   * Creates a handler that always responds with status 403 Forbidden and an
   * empty body.
   */
  def forbidden: Handler[Any, Nothing, Any, Response] =
    error(Status.Forbidden)

  /**
   * Creates a handler that always responds with status 403 Forbidden, like `error(Status.Forbidden, message)`: `message` is in the body only when the current [[ErrorResponseConfig]] has `withErrorBody` set, which by default is the case only in `Mode.Dev`.
   */
  def forbidden(message: => String): Handler[Any, Nothing, Any, Response] =
    error(Status.Forbidden, message)

  /**
   * Creates a handler from any value that has a [[ToHandler]] instance, such as
   * a [[Response]], a `ZIO` effect or a function. Same as the `handler`
   * function of the `zio.http` package.
   */
  def from[H](handler: => H)(implicit h: ToHandler[H]): Handler[h.Env, h.Err, h.In, h.Out] =
    h.toHandler(handler)

  /**
   * Creates a handler that always responds with status 200 OK and `body`.
   */
  def fromBody(body: => Body): Handler[Any, Nothing, Any, Response] =
    fromResponse(Response(body = body))

  /**
   * Creates a handler that fails with the `Left` value or succeeds with the
   * `Right` value of `either`.
   */
  def fromEither[Err, Out](either: => Either[Err, Out]): Handler[Any, Err, Any, Out] =
    fromExit(Exit.fromEither(either))

  /**
   * Creates a handler that completes with `exit`, evaluated each time the
   * handler runs. A non-fatal exception thrown while evaluating `exit` becomes
   * a defect.
   */
  def fromExit[Err, Out](exit: => Exit[Err, Out]): Handler[Any, Err, Any, Out] =
    new Handler[Any, Err, Any, Out] {
      override def apply(in: Any): ZIO[Any, Err, Out] =
        try exit
        catch {
          case error if NonFatal(error) => Exit.die(error)
        }
    }

  /**
   * Creates a handler from a pure function of the input. The type parameter is the input type, and the function comes in a second argument list: `Handler.fromFunction[Request](request => Response.text(request.method.name))`. A non-fatal exception thrown by the function becomes a defect.
   */
  def fromFunction[In]: FromFunction[In] = new FromFunction[In](())

  /**
   * Creates a handler from a function that returns another handler for the
   * input, and runs that handler on the same input.
   */
  def fromFunctionHandler[In]: FromFunctionHandler[In] = new FromFunctionHandler[In](())

  /**
   * Creates a handler from a function that returns an `Either`: a `Left` is a
   * failure and a `Right` is the output.
   */
  def fromFunctionEither[In]: FromFunctionEither[In] = new FromFunctionEither[In](())

  /**
   * Creates a handler from a function that returns a `Exit`, which is the
   * result of the handler.
   */
  def fromFunctionExit[In]: FromFunctionExit[In] = new FromFunctionExit[In](())

  /**
   * Creates a handler from a function that returns a `ZIO` effect, which the
   * handler runs.
   *
   * {{{
   * import zio._
   *
   * val userName: Handler[Any, Nothing, Request, Response] =
   *   Handler.fromFunctionZIO[Request] { request =>
   *     ZIO.succeed(Response.text(request.url.queryParam("name").getOrElse("guest")))
   *   }
   * }}}
   */
  def fromFunctionZIO[In]: FromFunctionZIO[In] = new FromFunctionZIO[In](())

  private[http] def determineMediaType(filePath: String): Option[MediaType] = {
    filePath.lastIndexOf(".") match {
      case -1 => None
      case i  =>
        // Extract file extension
        val ext = filePath.substring(i + 1)
        MediaType.forFileExtension(ext)
    }
  }

  /**
   * Creates a handler that serves the file `makeFile`, evaluated each time the
   * handler runs. Same as `fromFileZIO` with `ZIO.attempt(makeFile)`.
   */
  def fromFile[R](makeFile: => File, charset: Charset = Charsets.Utf8)(implicit
    trace: Trace,
  ): Handler[R, Throwable, Request, Response] =
    fromFileZIO(ZIO.attempt(makeFile), charset)

  /**
   * Creates a handler that serves the file returned by `getFile`, which runs on each request. The response has the headers `Accept-Ranges: bytes`, a weak `ETag`, `Last-Modified`, and a `Content-Type` found from the file extension, with `charset` for text types. `Range` requests get a 206 Partial Content or 416 Range Not Satisfiable response; when an `If-Range` header does not match the file's `ETag` or `Last-Modified`, the whole file is sent.
   *
   * The handler fails with a `java.io.FileNotFoundException` when the file does not exist, a `java.nio.file.AccessDeniedException` when it can't be read, and a `java.nio.file.NotDirectoryException` when it is a directory.
   */
  def fromFileZIO[R](getFile: ZIO[R, Throwable, File], charset: Charset = Charsets.Utf8)(implicit
    trace: Trace,
  ): Handler[R, Throwable, Request, Response] = {
    Handler
      .fromZIO[R, Throwable, (File, Long, Option[MediaType], Header.ETag, Header.LastModified)](
        ZIO.blocking {
          getFile.flatMap { file =>
            if (!file.exists()) {
              ZIO.fail(new FileNotFoundException())
            } else if (file.isFile && !file.canRead) {
              ZIO.fail(new AccessDeniedException(file.getAbsolutePath))
            } else if (!file.isFile) {
              ZIO.fail(new NotDirectoryException(s"Found directory instead of a file."))
            } else {
              ZIO.attemptBlocking {
                val fileSize      = file.length()
                val pathName      = file.toPath.toString
                val mediaType     = determineMediaType(pathName)
                val lastModified  = java.time.Instant.ofEpochMilli(file.lastModified())
                val etag          = Header.ETag.Weak(s"${file.lastModified().toHexString}-${fileSize.toHexString}")
                val lastModHeader =
                  Header.LastModified(java.time.ZonedDateTime.ofInstant(lastModified, java.time.ZoneOffset.UTC))
                (file, fileSize, mediaType, etag, lastModHeader)
              }
            }
          }
        },
      )
      .flatMap { case (file, fileSize, mediaType, etag, lastModified) =>
        Handler.fromFunctionZIO[Request] { request =>
          handleRangeRequest(request, file, fileSize, mediaType, charset, etag, lastModified)
        }
      }
  }

  private def handleRangeRequest(
    request: Request,
    file: File,
    fileSize: Long,
    mediaType: Option[MediaType],
    charset: Charset,
    etag: Header.ETag,
    lastModified: Header.LastModified,
  )(implicit trace: Trace): ZIO[Any, Throwable, Response] = {
    val rangeOpt   = request.headers.get(Header.Range)
    val ifRangeOpt = request.headers.get(Header.IfRange)

    // Check If-Range precondition
    val shouldProcessRange = ifRangeOpt match {
      case None                             => true // No If-Range, always process Range
      case Some(Header.IfRange.ETag(v))     =>
        // Compare ETag (strip quotes if needed)
        Header.ETag.render(etag).contains(v) || v == etag.asInstanceOf[Header.ETag.Weak].validator
      case Some(Header.IfRange.DateTime(v)) =>
        // Compare with Last-Modified
        !v.isBefore(lastModified.value)
    }

    // Only process "bytes" unit
    val isBytesRange = rangeOpt.exists {
      case Header.Range.Single(unit, _, _) => unit == "bytes"
      case Header.Range.Multiple(unit, _)  => unit == "bytes"
      case Header.Range.Suffix(unit, _)    => unit == "bytes"
      case Header.Range.Prefix(unit, _)    => unit == "bytes"
    }

    if (rangeOpt.isDefined && shouldProcessRange && isBytesRange) {
      handleByteRangeRequest(rangeOpt.get, file, fileSize, mediaType, charset, etag, lastModified)
    } else {
      // Return full file with Accept-Ranges header
      fullFileResponse(file, mediaType, charset, etag, lastModified)
    }
  }

  private def handleByteRangeRequest(
    range: Header.Range,
    file: File,
    fileSize: Long,
    mediaType: Option[MediaType],
    charset: Charset,
    etag: Header.ETag,
    lastModified: Header.LastModified,
  )(implicit trace: Trace): ZIO[Any, Throwable, Response] = {
    range match {
      case Header.Range.Single(_, start, endOpt) =>
        handleSingleRange(start, endOpt, file, fileSize, mediaType, charset, etag, lastModified)

      case Header.Range.Suffix(_, suffixLength) =>
        val start = (fileSize - suffixLength).max(0)
        handleSingleRange(start, Some(fileSize - 1), file, fileSize, mediaType, charset, etag, lastModified)

      case Header.Range.Prefix(_, start) =>
        handleSingleRange(start, None, file, fileSize, mediaType, charset, etag, lastModified)

      case Header.Range.Multiple(_, ranges) =>
        handleMultipleRanges(ranges, file, fileSize, mediaType, etag, lastModified)
    }
  }

  private def handleSingleRange(
    start: Long,
    endOpt: Option[Long],
    file: File,
    fileSize: Long,
    mediaType: Option[MediaType],
    charset: Charset,
    etag: Header.ETag,
    lastModified: Header.LastModified,
  )(implicit trace: Trace): ZIO[Any, Throwable, Response] = {
    val actualEnd = endOpt.map(e => Math.min(e, fileSize - 1)).getOrElse(fileSize - 1)

    if (start >= fileSize || start < 0 || start > actualEnd) {
      // 416 Range Not Satisfiable
      ZIO.succeed(
        Response
          .status(Status.RequestedRangeNotSatisfiable)
          .addHeader(Header.ContentRange.RangeTotal("bytes", fileSize.toInt)),
      )
    } else {
      val length = actualEnd - start + 1
      Body.fromFileRange(file, start, length).map { body =>
        val charset0 = mediaType.filter(mt => mt.mainType == "text" || !mt.binary).flatMap(_ => Some(charset))
        var response = Response(status = Status.PartialContent, body = body)
          .addHeader(Header.ContentRange.EndTotal("bytes", start.toInt, actualEnd.toInt, fileSize.toInt))
          .addHeader(Header.AcceptRanges.Bytes)
          .addHeader(etag)
          .addHeader(lastModified)

        mediaType.foreach { mt =>
          response = response.addHeader(Header.ContentType(mt, charset = charset0))
        }

        response
      }
    }
  }

  private def handleMultipleRanges(
    ranges: List[(Long, Option[Long])],
    file: File,
    fileSize: Long,
    mediaType: Option[MediaType],
    etag: Header.ETag,
    lastModified: Header.LastModified,
  )(implicit trace: Trace): ZIO[Any, Throwable, Response] = {
    // Validate all ranges and filter valid ones
    val validatedRanges = ranges.flatMap { case (start, endOpt) =>
      val actualEnd = endOpt.map(e => Math.min(e, fileSize - 1)).getOrElse(fileSize - 1)
      if (start >= 0 && start < fileSize && start <= actualEnd) {
        Some((start, actualEnd))
      } else {
        None
      }
    }

    if (validatedRanges.isEmpty) {
      ZIO.succeed(
        Response
          .status(Status.RequestedRangeNotSatisfiable)
          .addHeader(Header.ContentRange.RangeTotal("bytes", fileSize.toInt)),
      )
    } else {
      val contentType = mediaType.getOrElse(MediaType.application.`octet-stream`)

      val byteRanges = Chunk.fromIterable(validatedRanges.map { case (start, end) =>
        val length = end - start + 1
        Body.ByteRange(
          start = start,
          end = end,
          totalSize = fileSize,
          contentType = contentType,
          data =
            Body.FileBody(file, chunkSize = 4096, fileSize = fileSize, offset = start, length = Some(length)).asStream,
        )
      })

      Boundary.randomUUID.map { boundary =>
        val body = Body.fromMultipartByteRanges(byteRanges, boundary)
        Response(status = Status.PartialContent, body = body)
          .addHeader(
            Header.ContentType(MediaType.multipart.`byteranges`.copy(parameters = Map("boundary" -> boundary.id))),
          )
          .addHeader(Header.AcceptRanges.Bytes)
          .addHeader(etag)
          .addHeader(lastModified)
      }
    }
  }

  private def fullFileResponse(
    file: File,
    mediaType: Option[MediaType],
    charset: Charset,
    etag: Header.ETag,
    lastModified: Header.LastModified,
  )(implicit trace: Trace): ZIO[Any, Throwable, Response] = {
    Body.fromFile(file).map { body =>
      val charset0 = mediaType.filter(mt => mt.mainType == "text" || !mt.binary).flatMap(_ => Some(charset))
      var response = Response(body = body)
        .addHeader(Header.AcceptRanges.Bytes)
        .addHeader(etag)
        .addHeader(lastModified)

      mediaType.foreach { mt =>
        response = response.addHeader(Header.ContentType(mt, charset = charset0))
      }

      response
    }
  }

  /**
   * Creates a Handler that always succeeds with a 200 status code and the
   * provided ZStream with a known content length as the body
   */
  def fromStream[R](stream: ZStream[R, Throwable, String], contentLength: Long, charset: Charset = Charsets.Http)(
    implicit trace: Trace,
  ): Handler[R, Throwable, Any, Response] =
    Handler.fromZIO {
      ZIO.environment[R].map { env =>
        fromBody(Body.fromCharSequenceStream(stream.provideEnvironment(env), contentLength, charset))
      }
    }.flatten

  /**
   * Creates a Handler that always succeeds with a 200 status code and the
   * provided ZStream with a known content length as the body
   */
  def fromStream[R](stream: ZStream[R, Throwable, Byte], contentLength: Long)(implicit
    trace: Trace,
  ): Handler[R, Throwable, Any, Response] =
    Handler.fromZIO {
      ZIO.environment[R].map { env =>
        fromBody(Body.fromStream(stream.provideEnvironment(env), contentLength))
      }
    }.flatten

  /**
   * Creates a Handler that always succeeds with a 200 status code and the
   * provided ZStream as the body using chunked transfer encoding
   */
  def fromStreamChunked[R](stream: ZStream[R, Throwable, String], charset: Charset = Charsets.Http)(implicit
    trace: Trace,
  ): Handler[R, Throwable, Any, Response] =
    Handler.fromZIO {
      ZIO.environment[R].map { env =>
        fromBody(Body.fromCharSequenceStreamChunked(stream.provideEnvironment(env), charset))
      }
    }.flatten

  /**
   * Creates a Handler that always succeeds with a 200 status code and the
   * provided ZStream as the body using chunked transfer encoding
   */
  def fromStreamChunked[R](stream: ZStream[R, Throwable, Byte])(implicit
    trace: Trace,
  ): Handler[R, Throwable, Any, Response] =
    Handler.fromZIO {
      ZIO.environment[R].map { env =>
        fromBody(Body.fromStreamChunked(stream.provideEnvironment(env)))
      }
    }.flatten

  /**
   * Creates a handler that runs the effect `zio`, evaluated each time the
   * handler runs, and ignores its input.
   */
  def fromZIO[R, Err, Out](zio: => ZIO[R, Err, Out]): Handler[R, Err, Any, Out] =
    new Handler[R, Err, Any, Out] {
      override def apply(in: Any): ZIO[Scope & R, Err, Out] =
        try zio
        catch {
          case error if NonFatal(error) => ZIO.die(error)
        }
    }

  /**
   * Creates a handler which always responds with the provided Html page.
   */
  def html(view: => Html): Handler[Any, Nothing, Any, Response] =
    fromResponse(Response.html(view))

  /**
   * Creates a pass thru Handler instance
   */
  def identity[A]: Handler[Any, Nothing, A, A] =
    new Handler[Any, Nothing, A, A] {
      override def apply(in: A): ZIO[Any, Nothing, A] = Exit.succeed(in)
    }

  /**
   * Creates a handler that always responds with status 500 Internal Server
   * Error and an empty body.
   */
  def internalServerError: Handler[Any, Nothing, Any, Response] =
    error(Status.InternalServerError)

  /**
   * Creates a handler that always responds with status 500 Internal Server Error, like `error(Status.InternalServerError, message)`: `message` is in the body only when the current [[ErrorResponseConfig]] has `withErrorBody` set, which by default is the case only in `Mode.Dev`.
   */
  def internalServerError(message: => String): Handler[Any, Nothing, Any, Response] =
    error(Status.InternalServerError, message)

  /**
   * Creates a handler that always responds with status 405 Method Not Allowed
   * and an empty body.
   */
  def methodNotAllowed: Handler[Any, Nothing, Any, Response] =
    error(Status.MethodNotAllowed)

  /**
   * Creates a handler that always responds with status 405 Method Not Allowed, like `error(Status.MethodNotAllowed, message)`: `message` is in the body only when the current [[ErrorResponseConfig]] has `withErrorBody` set, which by default is the case only in `Mode.Dev`.
   */
  def methodNotAllowed(message: => String): Handler[Any, Nothing, Any, Response] =
    error(Status.MethodNotAllowed, message)

  /**
   * A handler that responds with status 404 Not Found, like `error(Status.NotFound, path)` with the request path as the message. The message is in the body only when the current [[ErrorResponseConfig]] has `withErrorBody` set, which by default is the case only in `Mode.Dev`.
   */
  val notFound: Handler[Any, Nothing, Request, Response] =
    Handler
      .fromFunctionHandler[Request] { request =>
        error(Status.NotFound, request.url.path.encode)
      }

  /**
   * Creates a handler that always responds with status 404 Not Found, like `error(Status.NotFound, message)`: `message` is in the body only when the current [[ErrorResponseConfig]] has `withErrorBody` set, which by default is the case only in `Mode.Dev`.
   */
  def notFound(message: => String): Handler[Any, Nothing, Any, Response] =
    error(Status.NotFound, message)

  /**
   * Creates a handler that responds with status 404 Not Found, like `error(Status.NotFound, path)` with the request path as the message. The message is in the body only when the current [[ErrorResponseConfig]] has `withErrorBody` set, which by default is the case only in `Mode.Dev`. The `routes` argument is not used: the result is the same as the `notFound` value.
   */
  def notFound(routes: Routes[_, _]): Handler[Any, Nothing, Request, Response] =
    Handler
      .fromFunctionHandler[Request] { request =>
        error(Status.NotFound, request.url.path.encode)
      }

  /**
   * A handler that always responds with status 200 OK and an empty body.
   */
  val ok: Handler[Any, Nothing, Any, Response] =
    status(Status.Ok)

  /**
   * Creates a builder for a handler that projects a part of its input with a
   * function `A => B`. It helps when handlers that take smaller inputs are
   * combined into one handler with a larger input.
   */
  def param[A]: ParamExtractorBuilder[A] =
    new ParamExtractorBuilder[A](())

  /**
   * Creates a handler that always succeeds with `response`, evaluated each time
   * the handler runs.
   */
  def fromResponse(response: => Response): Handler[Any, Nothing, Any, Response] =
    succeed(response)

  /**
   * Creates a handler that runs `getResponse` on each call and succeeds with
   * the response it produces.
   */
  def fromResponseZIO[R, Err](getResponse: ZIO[R, Err, Response]): Handler[R, Err, Any, Response] =
    fromZIO(getResponse)

  /**
   * Turns a handler that needs `Scope with R` into one that needs only `R`. A
   * handler always runs with a `Scope` (see `apply`), and the scoped resources
   * use that scope.
   */
  def scoped[R]: ScopedPartiallyApplied[R] =
    scopedPartiallyApplied.asInstanceOf[ScopedPartiallyApplied[R]]

  /**
   * Creates a handler that succeeds with the current ZIO stack trace.
   */
  def stackTrace(implicit trace: Trace): Handler[Any, Nothing, Any, StackTrace] =
    fromZIO(ZIO.stackTrace)

  /**
   * Creates a handler that always responds with the status `code` and an empty
   * body.
   */
  def status(code: => Status): Handler[Any, Nothing, Any, Response] =
    succeed(Response(code))

  /**
   * Creates a handler that always succeeds with `out`, evaluated each time the
   * handler runs.
   */
  def succeed[Out](out: => Out): Handler[Any, Nothing, Any, Out] =
    fromExit(Exit.succeed(out))

  /**
   * Creates a handler that responds with an HTML page made of the built-in
   * template, with `heading` as the title and heading, and `view` as the
   * content.
   */
  def template(heading: => CharSequence)(view: Html): Handler[Any, Nothing, Any, Response] =
    fromResponse(Response.html(Template.container(heading)(view)))

  /**
   * Creates a handler that always responds with status 200 OK and `text` as a
   * `text/plain` body.
   */
  def text(text: => CharSequence): Handler[Any, Nothing, Any, Response] =
    fromResponse(Response.text(text))

  /**
   * Creates a handler that responds with a 408 status code after the provided
   * time duration
   */
  def timeout(duration: Duration)(implicit trace: Trace): Handler[Any, Nothing, Any, Response] =
    status(Status.RequestTimeout).delay(duration)

  /**
   * Creates a handler which always responds with a 413 status code.
   */
  val tooLarge: Handler[Any, Nothing, Any, Response] =
    Handler.status(Status.RequestEntityTooLarge)

  /**
   * A handler that always succeeds with `()`.
   */
  val unit: Handler[Any, Nothing, Any, Unit] =
    fromExit(Exit.unit)

  /**
   * Creates a [[WebSocketApp]] from a function that uses the
   * [[WebSocketChannel]] of the connection.
   */
  final def webSocket[Env](
    f: WebSocketChannel => ZIO[Env, Throwable, Any],
  ): WebSocketApp[Env] =
    WebSocketApp(Handler.fromFunctionZIO(f))

  /**
   * Operations on request handlers (`Handler[R, Err, Request, Response]`) that
   * change the incoming request or the response.
   */
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
     * Updates the headers of the response produced by the handler with
     * `update`.
     */
    def updateHeaders(update: Headers => Headers)(implicit trace: Trace): RequestHandler[R, Err] =
      self.map(_.updateHeaders(update))
  }

  /**
   * Operations on handlers that produce a [[Response]], to read parts of the
   * response.
   */
  final implicit class ResponseOutputSyntax[-R, +Err, -In](val self: Handler[R, Err, In, Response]) extends AnyVal {

    /**
     * Returns a handler that succeeds with the body of the response.
     */
    def body(implicit trace: Trace): Handler[R, Err, In, Body] =
      self.map(_.body)

    /**
     * Returns a handler that succeeds with the `Content-Length` header of the
     * response, or `None` when the response has none.
     */
    def contentLength(implicit trace: Trace): Handler[R, Err, In, Option[Header.ContentLength]] =
      self.map(_.header(Header.ContentLength))

    /**
     * Returns a handler that succeeds with the `Content-Type` header of the
     * response, or `None` when the response has none.
     */
    def contentType(implicit trace: Trace): Handler[R, Err, In, Option[Header.ContentType]] =
      header(Header.ContentType)

    /**
     * Returns a handler that succeeds with the headers of the response.
     */
    def headers(implicit trace: Trace): Handler[R, Err, In, Headers] =
      self.map(_.headers)

    /**
     * Returns a handler that succeeds with the header of type `headerType` from
     * the response, or `None` when the response has no such header or it does
     * not parse.
     */
    def header(headerType: HeaderType)(implicit
      trace: Trace,
    ): Handler[R, Err, In, Option[headerType.HeaderValue]] =
      self.map(_.header(headerType))

    /**
     * Returns a handler that succeeds with the header of type `headerType` from
     * the response, or `None` when the response has no such header. It fails
     * with the parse error message when the header does not parse.
     */
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

    /**
     * Returns a handler that succeeds with the unparsed value of the header
     * `name` from the response, or `None` when the response has no such header.
     */
    def rawHeader(name: CharSequence)(implicit trace: Trace): Handler[R, Err, In, Option[String]] =
      self.map(_.rawHeader(name))

    /**
     * Returns a handler that succeeds with the status of the response.
     */
    def status(implicit trace: Trace): Handler[R, Err, In, Status] =
      self.map(_.status)
  }

  /**
   * Builder returned by `Handler#contraFlatMap`.
   */
  final class ContraFlatMap[-R, +Err, -In, +Out, In1](val self: Handler[R, Err, In, Out]) extends AnyVal {

    /**
     * Returns a handler that runs the handler that `f` returns for its input,
     * and passes that handler's output to the original handler.
     */
    def apply[R1 <: R, Err1 >: Err](f: In1 => Handler[R1, Err1, Any, In])(implicit
      trace: Trace,
    ): Handler[R1, Err1, In1, Out] =
      fromFunctionHandler(f) >>> self
  }

  /**
   * Builder returned by `Handler.fromFunction`.
   */
  final class FromFunction[In](val self: Unit) extends AnyVal {

    /**
     * Creates a handler that applies `f` to its input. A non-fatal exception
     * thrown by `f` becomes a defect.
     */
    def apply[Out](f: In => Out): Handler[Any, Nothing, In, Out] =
      new Handler[Any, Nothing, In, Out] {
        override def apply(in: In): ZIO[Any, Nothing, Out] =
          try {
            Exit.succeed(f(in))
          } catch {
            case error if NonFatal(error) => Exit.die(error)
          }
      }
  }

  /**
   * Builder returned by `Handler.fromFunctionHandler`.
   */
  final class FromFunctionHandler[In](val self: Unit) extends AnyVal {

    /**
     * Creates a handler that runs the handler that `f` returns for its input,
     * on the same input. A non-fatal exception thrown by `f` becomes a defect.
     */
    def apply[R, Err, Out](f: In => Handler[R, Err, In, Out]): Handler[R, Err, In, Out] =
      new Handler[R, Err, In, Out] {
        override def apply(in: In): ZIO[Scope & R, Err, Out] =
          try f(in)(in)
          catch {
            case error if NonFatal(error) => ZIO.die(error)
          }
      }
  }

  /**
   * Builder returned by `Handler.fromFunctionEither`.
   */
  final class FromFunctionEither[In](val self: Unit) extends AnyVal {

    /**
     * Creates a handler that fails with the `Left` value or succeeds with the
     * `Right` value that `f` returns. A non-fatal exception thrown by `f`
     * becomes a defect.
     */
    def apply[R, Err, Out](f: In => Either[Err, Out]): Handler[Any, Err, In, Out] =
      new Handler[Any, Err, In, Out] {
        override def apply(in: In): ZIO[Any, Err, Out] =
          try {
            Exit.fromEither(f(in))
          } catch {
            case error if NonFatal(error) => Exit.die(error)
          }
      }
  }

  /**
   * Builder returned by `Handler.fromFunctionExit`.
   */
  final class FromFunctionExit[In](val self: Unit) extends AnyVal {

    /**
     * Creates a handler that completes with the `Exit` that `f` returns. A
     * non-fatal exception thrown by `f` becomes a defect.
     */
    def apply[R, Err, Out](f: In => Exit[Err, Out]): Handler[Any, Err, In, Out] =
      new Handler[Any, Err, In, Out] {
        override def apply(in: In): ZIO[Any, Err, Out] =
          try {
            f(in)
          } catch {
            case error if NonFatal(error) => Exit.die(error)
          }
      }
  }

  /**
   * Builder returned by `Handler.fromFunctionZIO`.
   */
  final class FromFunctionZIO[In](val self: Unit) extends AnyVal {

    /**
     * Creates a handler that runs the effect that `f` returns. A non-fatal
     * exception thrown by `f` becomes a defect.
     */
    def apply[R, Err, Out](f: In => ZIO[R, Err, Out]): Handler[R, Err, In, Out] =
      new Handler[R, Err, In, Out] {
        override def apply(in: In): ZIO[R, Err, Out] =
          try f(in)
          catch {
            case error if NonFatal(error) => ZIO.die(error)
          }
      }
  }

  /**
   * Builder returned by `Handler.param`.
   */
  final class ParamExtractorBuilder[A](val unit: Unit) extends AnyVal {

    /**
     * Creates a handler that succeeds with `project` applied to its input.
     */
    def apply[B](project: A => B): Handler[Any, Nothing, A, B] =
      Handler.identity[B].contramap[A](project)
  }

  /**
   * Builder returned by `Handler.scoped`.
   */
  final class ScopedPartiallyApplied[R](private val dummy: Boolean = true) extends AnyVal {

    /**
     * Returns `handler` with `Scope` removed from its environment type.
     */
    def apply[Err, In, Out](handler: Handler[Scope with R, Err, In, Out])(implicit
      trace: Trace,
    ): Handler[R, Err, In, Out] =
      handler.asInstanceOf[Handler[R, Err, In, Out]]
  }

  private val scopedPartiallyApplied: ScopedPartiallyApplied[Any] = new ScopedPartiallyApplied[Any]()
}
