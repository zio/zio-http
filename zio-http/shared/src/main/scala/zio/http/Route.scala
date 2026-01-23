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

import zio.Cause.Fail
import zio._

import zio.http.Route.CheckResponse
import zio.http.codec.PathCodec

/*
 * Represents a single route, which has either handled its errors by converting
 * them into responses, or which has polymorphic errors, which must later be
 * converted into responses before the route can be executed.
 *
 * Routes have the property that, before conversion into handlers, they will
 * fully handle all errors, including defects, translating them appropriately
 * into responses that can be delivered to clients. Thus, the handlers returned
 * by `toHandler` will never fail, and will always produce a valid response.
 *
 * Individual routes can be aggregated using [[zio.http.Routes]].
 */
sealed trait Route[-Env, +Err] { self =>
  import Route.{Augmented, Handled, Provided, Unhandled}

  /**
   * Applies the route to the specified request. The route must be defined for
   * the request, or else this method will fail fatally. Note that you may only
   * call this function when you have handled all errors produced by the route,
   * converting them into responses.
   */
  final def apply(request: Request)(implicit ev: Err <:< Response, trace: Trace): ZIO[Scope & Env, Response, Response] =
    toHandler.apply(request)

  def asErrorType[Err2](implicit ev: Err <:< Err2): Route[Env, Err2] = self.asInstanceOf[Route[Env, Err2]]

  /**
   * Handles all typed errors in the route by converting them into responses.
   * This method can be used to convert a route that does not handle its errors
   * into one that does handle its errors.
   *
   * If the underlying handler uses the error channel to send responses, this
   * method will not pass the responses to the provided function.
   */
  final def handleError(f: Err => Response)(implicit trace: Trace): Route[Env, Nothing] =
    self.handleErrorCauseZIO(c => ErrorResponseConfig.configRef.get.map(Response.fromCauseWith(c, _)(f)))

  /**
   * Handles all typed errors in the route by converting them into a zio effect
   * that produces a response. This method can be used to convert a route that
   * does not handle its errors into one that does handle its errors.
   *
   * If the underlying handler uses the error channel to send responses, this
   * method will not pass the responses to the provided function.
   */
  final def handleErrorZIO[Env1 <: Env](
    f: Err => ZIO[Env1, Nothing, Response],
  )(implicit trace: Trace): Route[Env1, Nothing] =
    self.handleErrorCauseZIO { cause =>
      cause.failureOrCause match {
        case Left(err)    => f(err)
        case Right(cause) => ErrorResponseConfig.configRef.getWith(c => ZIO.succeed(Response.fromCause(cause, c)))
      }
    }

  /**
   * Handles all typed errors, as well as all non-recoverable errors, by
   * converting them into responses. This method can be used to convert a route
   * that does not handle its errors into one that does handle its errors.
   *
   * If the underlying handler uses the error channel to send responses, this
   * method will not pass the responses to the provided function.
   */
  final def handleErrorCause(f: Cause[Err] => Response)(implicit trace: Trace): Route[Env, Nothing] =
    self match {
      case Provided(route, env)                     => Provided(route.handleErrorCause(f), env)
      case Augmented(route, aspect)                 => Augmented(route.handleErrorCause(f), aspect)
      case Handled(routePattern, handler, location) =>
        Handled(routePattern, handler.map(_.mapErrorCause(_.failureOrCause.fold(identity, f))), location)

      case Unhandled(pattern, handler0, zippable, location) =>
        val handler2: Handler[Any, Nothing, RoutePattern[_], Handler[Env, Response, Request, Response]] =
          handler { (pattern: RoutePattern[_]) =>
            val paramHandler =
              handler { (request: Request) =>
                pattern.decode(request.method, request.path) match {
                  case Left(error)   => ZIO.dieMessage(error)
                  case Right(params) =>
                    handler0.asInstanceOf[Handler[Any, Err, Any, Response]](
                      zippable.asInstanceOf[Zippable[Any, Any]].zip(params, request),
                    )
                }
              }

            Handler.scoped(paramHandler.mapErrorCause(f))
          }

        Handled(pattern, handler2, location)
    }

  /**
   * Handles all typed errors, as well as all non-recoverable errors, by
   * converting them into a ZIO effect that produces the response. This method
   * can be used to convert a route that does not handle its errors into one
   * that does handle its errors.
   *
   * If the underlying handler uses the error channel to send responses, this
   * method will not pass the responses to the provided function.
   */
  final def handleErrorCauseZIO[Env1 <: Env](
    f: Cause[Err] => ZIO[Env1, Nothing, Response],
  )(implicit trace: Trace): Route[Env1, Nothing] =
    self match {
      case Provided(route, env)                     =>
        Route.handledIgnoreParams(route.routePattern)(
          Handler.fromZIO(ZIO.environment[Env1]).flatMap { (env1: ZEnvironment[Env1]) =>
            val env0 = env.asInstanceOf[ZEnvironment[Any]] ++ env1.asInstanceOf[ZEnvironment[Any]]
            route
              .handleErrorCauseZIO(f)
              .toHandler
              .asInstanceOf[Handler[Any, Response, Request, Response]]
              .provideEnvironment(env0)
          },
        )
      case Augmented(route, aspect)                 =>
        Augmented(route.handleErrorCauseZIO(f).asInstanceOf[Route[Any, Nothing]], aspect)
      case Handled(routePattern, handler, location) =>
        Handled(routePattern, handler.map(_.mapErrorCauseZIO(_.failureOrCause.fold(ZIO.succeed(_), f))), location)

      case Unhandled(pattern, handler0, zippable, location) =>
        val handler2: Handler[Any, Nothing, RoutePattern[_], Handler[Env1, Response, Request, Response]] = {
          handler { (pattern: RoutePattern[_]) =>
            val paramHandler =
              handler { (request: Request) =>
                pattern.decode(request.method, request.path) match {
                  case Left(error)   => ZIO.dieMessage(error)
                  case Right(params) =>
                    handler0.asInstanceOf[Handler[Any, Err, Any, Response]](
                      zippable.asInstanceOf[Zippable[Any, Any]].zip(params, request),
                    )
                }
              }
            Handler.scoped[Env1](paramHandler.mapErrorCauseZIO(f))
          }
        }

        Handled(pattern, handler2, location)
    }

  /**
   * Effectfully peeks at the unhandled failure of this Route.
   */
  final def tapErrorZIO[Err1 >: Err](
    f: Err => ZIO[Any, Err1, Any],
  )(implicit trace: Trace, ev: CheckResponse[Err]): Route[Env, Err1] =
    self match {
      case Provided(route, env)                        => Provided(route.tapErrorZIO(f), env)
      case Augmented(route, aspect)                    => Augmented(route.tapErrorZIO(f), aspect)
      case handled @ Handled(_, _, _)                  => handled
      case Unhandled(rpm, handler, zippable, location) => Unhandled(rpm, handler.tapErrorZIO(f), zippable, location)
    }

  /**
   * Effectfully peeks at the unhandled failure cause of this Route.
   */
  final def tapErrorCauseZIO[Err1 >: Err](
    f: Cause[Err] => ZIO[Any, Err1, Any],
  )(implicit trace: Trace, ev: CheckResponse[Err]): Route[Env, Err1] =
    self match {
      case Provided(route, env)                        =>
        Provided(route.tapErrorCauseZIO(f), env)
      case Augmented(route, aspect)                    =>
        Augmented(route.tapErrorCauseZIO(f), aspect)
      case Handled(routePattern, handler, location)    =>
        Handled(
          routePattern,
          handler.map(_.tapErrorCauseZIO { cause0 =>
            f(cause0.asInstanceOf[Cause[Nothing]]).catchAllCause(cause => ZIO.fail(Response.fromCause(cause)))
          }),
          location,
        )
      case Unhandled(rpm, handler, zippable, location) =>
        Unhandled(rpm, handler.tapErrorCauseZIO(f), zippable, location)
    }

  /**
   * Allows the transformation of the Err type through a function allowing one
   * to build up a Routes in Stages targets the Unhandled case
   */
  final def mapError[Err1](fxn: Err => Err1)(implicit trace: Trace): Route[Env, Err1] = {
    self match {
      case Provided(route, env)                        => Provided(route.mapError(fxn), env)
      case Augmented(route, aspect)                    => Augmented(route.mapError(fxn), aspect)
      case Handled(routePattern, handler, location)    => Handled(routePattern, handler, location)
      case Unhandled(rpm, handler, zippable, location) => Unhandled(rpm, handler.mapError(fxn), zippable, location)
    }

  }

  /**
   * Allows the transformation of the Err type through an Effectful program
   * allowing one to build up a Routes in Stages targets the Unhandled case
   * only.
   */
  final def mapErrorZIO[Err1](fxn: Err => ZIO[Any, Err1, Response])(implicit trace: Trace): Route[Env, Err1] =
    self match {
      case Provided(route, env)                        => Provided(route.mapErrorZIO(fxn), env)
      case Augmented(route, aspect)                    => Augmented(route.mapErrorZIO(fxn), aspect)
      case Handled(routePattern, handler, location)    => Handled(routePattern, handler, location)
      case Unhandled(rpm, handler, zippable, location) => Unhandled(rpm, handler.mapErrorZIO(fxn), zippable, location)
    }

  /**
   * Handles all typed errors in the route by converting them into responses,
   * taking into account the request that caused the error. This method can be
   * used to convert a route that does not handle its errors into one that does
   * handle its errors.
   *
   * If the underlying handler uses the error channel to send responses, this
   * method will not pass the responses to the provided function.
   */
  final def handleErrorRequest(f: (Err, Request) => Response)(implicit trace: Trace): Route[Env, Nothing] =
    self.handleErrorRequestCauseZIO((request, cause) =>
      ErrorResponseConfig.configRef.get.map(Response.fromCauseWith(cause, _)(f(_, request))),
    )

  /**
   * Handles all typed errors, as well as all non-recoverable errors, by
   * converting them into responses, taking into account the request that caused
   * the error. This method can be used to convert a route that does not handle
   * its errors into one that does handle its errors.
   *
   * If the underlying handler uses the error channel to send responses, this
   * method will not pass the responses to the provided function.
   */
  final def handleErrorRequestCause(f: (Request, Cause[Err]) => Response)(implicit trace: Trace): Route[Env, Nothing] =
    self match {
      case Provided(route, env)                      => Provided(route.handleErrorRequestCause(f), env)
      case Augmented(route, aspect)                  => Augmented(route.handleErrorRequestCause(f), aspect)
      case Handled(routePattern, handler0, location) =>
        Handled(
          routePattern,
          handler0.map { h =>
            Handler.fromFunctionHandler { (req: Request) =>
              h.mapErrorCause(_.failureOrCause.fold(identity, f(req, _)))
            }
          },
          location,
        )

      case Unhandled(pattern, handler0, zippable, location) =>
        val handler2: Handler[Any, Nothing, RoutePattern[_], Handler[Env, Response, Request, Response]] = {
          handler { (pattern: RoutePattern[_]) =>
            val paramHandler =
              handler { (request: Request) =>
                pattern.decode(request.method, request.path) match {
                  case Left(error)   => ZIO.dieMessage(error)
                  case Right(params) =>
                    handler0.asInstanceOf[Handler[Any, Err, Any, Response]](
                      zippable.asInstanceOf[Zippable[Any, Any]].zip(params, request),
                    )
                }
              }

            // Sandbox before applying aspect:
            Handler.scoped(Handler.fromFunctionHandler((req: Request) => paramHandler.mapErrorCause(f(req, _))))
          }
        }

        Handled(pattern, handler2, location)
    }

  /**
   * Handles all typed errors, as well as all non-recoverable errors, by
   * converting them into a ZIO effect that produces the response, taking into
   * account the request that caused the error. This method can be used to
   * convert a route that does not handle its errors into one that does handle
   * its errors.
   *
   * If the underlying handler uses the error channel to send responses, this
   * method will not pass the responses to the provided function.
   */
  final def handleErrorRequestCauseZIO[Env1 <: Env](
    f: (Request, Cause[Err]) => ZIO[Env1, Nothing, Response],
  )(implicit trace: Trace): Route[Env1, Nothing] =
    self match {
      case Provided(route, env)                     =>
        Route.handledIgnoreParams(route.routePattern)(
          Handler.fromZIO(ZIO.environment[Env1]).flatMap { (env1: ZEnvironment[Env1]) =>
            val env0 = env.asInstanceOf[ZEnvironment[Any]] ++ env1.asInstanceOf[ZEnvironment[Any]]
            route
              .handleErrorRequestCauseZIO(f)
              .toHandler
              .asInstanceOf[Handler[Any, Response, Request, Response]]
              .provideEnvironment(env0)
          },
        )
      case Augmented(route, aspect)                 =>
        Augmented(route.handleErrorRequestCauseZIO(f).asInstanceOf[Route[Any, Nothing]], aspect)
      case Handled(routePattern, handler, location) =>
        Handled(
          routePattern,
          handler.map { handler =>
            Handler.fromFunctionHandler { (req: Request) =>
              handler.mapErrorCauseZIO(_.failureOrCause.fold(ZIO.succeed(_), f(req, _)))
            }
          },
          location,
        )

      case Unhandled(routePattern, handler0, zippable, location) =>
        val handler2: Handler[Any, Nothing, RoutePattern[_], Handler[Env1, Response, Request, Response]] =
          handler { (pattern: RoutePattern[_]) =>
            val paramHandler =
              handler { (request: Request) =>
                pattern.decode(request.method, request.path) match {
                  case Left(error)   => ZIO.dieMessage(error)
                  case Right(params) =>
                    handler0.asInstanceOf[Handler[Any, Err, Any, Response]](
                      zippable.asInstanceOf[Zippable[Any, Any]].zip(params, request),
                    )
                }
              }
            Handler.scoped[Env1](
              Handler.fromFunctionHandler((req: Request) => paramHandler.mapErrorCauseZIO(f(req, _))),
            )
          }

        Handled(routePattern, handler2, location)
    }

  /**
   * Determines if the route is defined for the specified request.
   */
  final def isDefinedAt(request: Request): Boolean = routePattern.matches(request.method, request.path)

  /**
   * The location where the route was created, which is useful for debugging
   * purposes.
   */
  def location: Trace

  def nest(prefix: PathCodec[Unit]): Route[Env, Err] =
    self match {
      case Provided(route, env)                => Provided(route.nest(prefix), env)
      case Augmented(route, aspect)            => Augmented(route.nest(prefix), aspect)
      case Handled(pattern, handler, location) => Handled(pattern.nest(prefix), handler, location)

      case Unhandled(pattern, handler, zippable, location) =>
        Unhandled(pattern.nest(prefix), handler, zippable, location)
    }

  final def provideEnvironment(env: ZEnvironment[Env]): Route[Any, Err] =
    Route.Provided(self, env)

  /**
   * The route pattern over which the route is defined. The route can only
   * handle requests that match this route pattern.
   */
  def routePattern: RoutePattern[_]

  /**
   * Applies the route to the specified request. The route must be defined for
   * the request, or else this method will fail fatally.
   */
  final def run(request: Request)(implicit trace: Trace): ZIO[Scope & Env, Either[Err, Response], Response] =
    Routes(self).run(request)

  /**
   * Returns a route that automatically translates all failures into responses,
   * using best-effort heuristics to determine the appropriate HTTP status code.
   * Based on the currently configured `ErrorResponseConfig`, the response will
   * have a body that may include a message and a stack trace.
   */
  final def sandbox(implicit trace: Trace): Route[Env, Nothing] =
    handleErrorCauseZIO { cause =>
      ZIO.logErrorCause("Unhandled exception in request handler", cause) *>
        ErrorResponseConfig.configRef.getWith(cfg => Exit.succeed(Response.fromCause(cause, cfg)))
    }

  /**
   * Applies a middleware aspect to this route.
   */
  final def @@[Env1 <: Env](aspect: Middleware[Env1]): Route[Env1, Err] =
    aspect(self.toRoutes).routes.head.asInstanceOf[Route[Env1, Err]]

  /**
   * Applies a handler aspect that does not provide context to this route.
   */
  final def @@[Env0](aspect: HandlerAspect[Env0, Unit]): Route[Env with Env0, Err] =
    self.transform[Env with Env0](handler => handler @@ aspect)

  /**
   * Applies a handler aspect that provides context to this route.
   * The aspect is applied after path parameters are decoded, so the handler
   * receives a plain Request rather than a tuple of (params, request).
   */
  final def @@[Env0, Ctx <: Env](aspect: HandlerAspect[Env0, Ctx])(implicit
    tag: Tag[Ctx],
  ): Route[Env0, Err] =
    self.transform[Env0](handler => handler @@ aspect)

  def toHandler(implicit ev: Err <:< Response, trace: Trace): Handler[Env, Response, Request, Response]

  final def toRoutes: Routes[Env, Err] = Routes(self)

  def transform[Env1](
    f: Handler[Env, Response, Request, Response] => Handler[Env1, Response, Request, Response],
  ): Route[Env1, Err] =
    Route.Augmented(self, f)
}
object Route                   {

  def handledIgnoreParams[Env](
    routePattern: RoutePattern[_],
  )(handler0: Handler[Env, Response, Request, Response])(implicit trace: Trace): Route[Env, Nothing] =
    Route.Handled(routePattern, handler((_: RoutePattern[_]) => handler0), Trace.empty)

  def handled[Params, Env](rpm: RoutePattern[Params]): HandledConstructor[Env, Params] =
    new HandledConstructor[Env, Params](rpm)

  val notFound: Route[Any, Nothing] =
    Handled(RoutePattern.any, handler((_: RoutePattern[_]) => Handler.notFound), Trace.empty)

  def route[Params, Env](rpm: RoutePattern[Params]): UnhandledConstructor[Env, Params] =
    new UnhandledConstructor[Env, Params](rpm)

  final class HandledConstructor[-Env, Params](val pattern: RoutePattern[Params]) extends AnyVal {
    def apply[Env1 <: Env, In](
      handler0: Handler[Env1, Response, In, Response],
    )(implicit zippable: Zippable.Out[Params, Request, In], trace: Trace): Route[Env1, Nothing] = {
      val handler2: Handler[Any, Nothing, RoutePattern[Params], Handler[Env1, Response, Request, Response]] = {
        handler { (pattern: RoutePattern[Params]) =>
          Handler.scoped[Env1](
            handler { (request: Request) =>
              pattern.decode(request.method, request.path) match {
                case Left(error)   => ZIO.dieMessage(error)
                case Right(params) => handler0(zippable.zip(params, request))
              }
            },
          )
        }
      }

      Handled(pattern, handler2, trace)
    }
  }

  final class UnhandledConstructor[-Env, Params](val rpm: RoutePattern[Params]) extends AnyVal {
    def apply[Env1 <: Env, Err, Input](
      handler: Handler[Env1, Err, Input, Response],
    )(implicit zippable: Zippable.Out[Params, Request, Input], trace: Trace): Route[Env1, Err] =
      Unhandled(rpm, handler, zippable, trace)
  }

  private final case class Provided[Env, +Err](
    route: Route[Env, Err],
    env: ZEnvironment[Env],
  ) extends Route[Any, Err] {
    def location: Trace = route.location

    def routePattern: RoutePattern[_] = route.routePattern

    override def toHandler(implicit ev: Err <:< Response, trace: Trace): Handler[Any, Response, Request, Response] =
      route.toHandler.provideEnvironment(env)

    override def toString = s"Route.Provided(${route}, ${env})"
  }

  private final case class Augmented[InEnv, -OutEnv, +Err](
    route: Route[InEnv, Err],
    aspect: Handler[InEnv, Response, Request, Response] => Handler[OutEnv, Response, Request, Response],
  ) extends Route[OutEnv, Err] {
    def location: Trace = route.location

    def routePattern: RoutePattern[_] = route.routePattern

    override def toHandler(implicit ev: Err <:< Response, trace: Trace): Handler[OutEnv, Response, Request, Response] =
      aspect(route.toHandler)

    override def toString = s"Route.Augmented(${route}, ${aspect})"
  }

  private final case class Handled[-Env, Params](
    routePattern: RoutePattern[Params],
    handler: Handler[Any, Nothing, RoutePattern[Params], Handler[Env, Response, Request, Response]],
    location: Trace,
  ) extends Route[Env, Nothing] {
    override def toHandler(implicit ev: Nothing <:< Response, trace: Trace): Handler[Env, Response, Request, Response] =
      Handler.scoped[Env] {
        Handler
          .fromZIO(handler(routePattern).map(_.sandbox))
          .flatten
      }

    override def toString = s"Route.Handled(${routePattern}, ${location})"
  }

  private final case class Unhandled[Params, Input, -Env, +Err](
    routePattern: RoutePattern[Params],
    handler: Handler[Env, Err, Input, Response],
    zippable: Zippable.Out[Params, Request, Input],
    location: Trace,
  ) extends Route[Env, Err] { self =>

    override def toHandler(implicit ev: Err <:< Response, trace: Trace): Handler[Env, Response, Request, Response] = {
      convert(handler.asErrorType[Response])
    }

    override def toString = s"Route.Unhandled(${routePattern}, ${location})"

    private def convert[Env1 <: Env](
      handler: Handler[Env1, Response, Input, Response],
    )(implicit trace: Trace): Handler[Env1, Response, Request, Response] = {
      implicit val z = zippable

      Route.handled(routePattern)(handler).toHandler
    }
  }

  sealed trait CheckResponse[-A] { def isResponse: Boolean }
  object CheckResponse           {
    implicit val response: CheckResponse[Response] = new CheckResponse[Response] {
      val isResponse = true
    }

    // to avoid unnecessary allocation
    private val otherInstance: CheckResponse[Nothing] = new CheckResponse[Nothing] {
      val isResponse = false
    }
    implicit def other[A]: CheckResponse[A]           = otherInstance.asInstanceOf[CheckResponse[A]]
  }
}
