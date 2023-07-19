package zio.http

import zio._

import zio.http.Route.Provide

/**
 * Represents a single route, which has either handled its errors by converting
 * them into responses, or which has polymorphic errors, which must later be
 * converted into responses before the route can be executed.
 */
sealed trait Route[-Env, +Err] { self =>
  import Route.{Augmented, Handled, Provide, Unhandled}

  /**
   * Augments this route with the specified middleware.
   */
  def @@[Env1 <: Env](aspect: RouteAspect[Nothing, Env1]): Route[Env1, Err] =
    Route.Augmented(self, aspect)

  /**
   * Applies the route to the specified request. The route must be defined for
   * the request, or else this method will fail fatally. Note that you may only
   * call this function when you have handled all errors produced by the route,
   * converting them into responses.
   */
  final def apply(request: Request)(implicit ev: Err <:< Response): ZIO[Env, Response, Response] =
    toHandler.apply(request)

  def asErrorType[Err2](implicit ev: Err <:< Err2): Route[Env, Err2] = self.asInstanceOf[Route[Env, Err2]]

  /**
   * Handles the error of the route. This method can be used to convert a route
   * that does not handle its errors into one that does handle its errors.
   */
  final def handleError(f: Err => Response): Route[Env, Nothing] =
    self.handleErrorCause { cause =>
      cause.failureOrCause match {
        case Left(failure) => f(failure)
        case Right(cause)  => Response.fromCause(cause)
      }
    }

  final def handleErrorCause(f: Cause[Err] => Response): Route[Env, Nothing] =
    self match {
      case Provide(route, env)                      => Provide(route.handleErrorCause(f), env)
      case Augmented(route, aspect)                 => Augmented(route.handleErrorCause(f), aspect)
      case Handled(routePattern, handler, location) =>
        val f2: Cause[Response] => Response = f.asInstanceOf[Cause[Response] => Response]

        Handled(routePattern, handler.mapErrorCause(f2), location)

      case Unhandled(rpm, handler, zippable, location) =>
        val handler2: Handler[Env, Response, Request, Response] = {
          val paramHandler =
            Handler.fromFunctionZIO[(rpm.Context, Request)] { case (ctx, request) =>
              rpm.routePattern.decode(request.method, request.path) match {
                case Left(error)  => ZIO.dieMessage(error)
                case Right(value) =>
                  val params = rpm.zippable.zip(value, ctx)

                  handler(zippable.zip(params, request))
              }
            }

          rpm.middleware.applyContext(paramHandler.mapErrorCause(f))
        }

        Handled(rpm.routePattern, handler2, location)
    }

  /**
   * Determines if the route is defined for the specified request.
   */
  final def isDefinedAt(request: Request): Boolean = routePattern.matches(request.method, request.path)

  def location: Trace

  final def provideEnvironment(env: ZEnvironment[Env]): Route[Any, Err] =
    Route.Provide(self, env)

  /**
   * The route pattern over which the route is defined. The route can only
   * handle requests that match this route pattern.
   */
  def routePattern: RoutePattern[_]

  /**
   * Returns a route that automatically translates all failures into responses,
   * using best-effort heuristics to determine the appropriate HTTP status code,
   * and attaching error details using the HTTP header `Warning`.
   */
  final def sandbox: Route[Env, Nothing] =
    handleErrorCause(Response.fromCause(_))

  def toHandler(implicit ev: Err <:< Response): Handler[Env, Response, Request, Response]

  final def toHttpApp(implicit ev: Err <:< Response): HttpApp[Env] = Routes(self).toHttpApp
}
object Route                   {

  def handled[Params](routePattern: RoutePattern[Params]): HandledConstructor[Any, Params] =
    handled(RoutePatternMiddleware(routePattern, Middleware.identity))

  def handled[Params, Env](rpm: RoutePatternMiddleware[Env, Params]): HandledConstructor[Env, Params] =
    new HandledConstructor[Env, Params](rpm)

  def route[Params](routePattern: RoutePattern[Params]): UnhandledConstructor[Any, Params] =
    route(RoutePatternMiddleware(routePattern, Middleware.identity))

  def route[Params, Env](rpm: RoutePatternMiddleware[Env, Params]): UnhandledConstructor[Env, Params] =
    new UnhandledConstructor[Env, Params](rpm)

  final class HandledConstructor[-Env, Params](val rpm: RoutePatternMiddleware[Env, Params]) extends AnyVal {
    def apply[Env1 <: Env, In](
      handler: Handler[Env1, Response, In, Response],
    )(implicit zippable: Zippable.Out[Params, Request, In], trace: Trace): Route[Env1, Nothing] = {
      val handler2: Handler[Env1, Response, Request, Response] = {
        val paramHandler =
          Handler.fromFunctionZIO[(rpm.Context, Request)] { case (ctx, request) =>
            rpm.routePattern.decode(request.method, request.path) match {
              case Left(error)  => ZIO.dieMessage(error)
              case Right(value) =>
                val params = rpm.zippable.zip(value, ctx)

                handler(zippable.zip(params, request))
            }
          }

        rpm.middleware.applyContext(paramHandler)
      }

      Handled(rpm.routePattern, handler2, trace)
    }
  }

  final class UnhandledConstructor[-Env, Params](val rpm: RoutePatternMiddleware[Env, Params]) extends AnyVal {
    def apply[Env1 <: Env, Err, Input](
      handler: Handler[Env1, Err, Input, Response],
    )(implicit zippable: Zippable.Out[Params, Request, Input], trace: Trace): Route[Env1, Err] =
      Unhandled(rpm, handler, zippable, trace)
  }

  private[http] final case class Provide[Env, +Err](
    route: Route[Env, Err],
    env: ZEnvironment[Env],
  ) extends Route[Any, Err] {
    def location: Trace = route.location

    def routePattern: RoutePattern[_] = route.routePattern

    override def toHandler(implicit ev: Err <:< Response): Handler[Any, Response, Request, Response] =
      route.toHandler.provideEnvironment(env)

    override def toString() = s"Route.Provide(${route}, ${env})"
  }

  private[http] final case class Augmented[-Env, +Err](
    route: Route[Env, Err],
    aspect: RouteAspect[Nothing, Env],
  ) extends Route[Env, Err] {
    def location: Trace = route.location

    def routePattern: RoutePattern[_] = route.routePattern

    override def toHandler(implicit ev: Err <:< Response): Handler[Env, Response, Request, Response] =
      aspect(route.toHandler)

    override def toString() = s"Route.Augmented(${route}, ${aspect})"
  }

  private[http] final case class Handled[-Env](
    routePattern: RoutePattern[_],
    handler: Handler[Env, Response, Request, Response],
    location: Trace,
  ) extends Route[Env, Nothing] {
    override def toHandler(implicit ev: Nothing <:< Response): Handler[Env, Response, Request, Response] =
      handler

    override def toString() = s"Route.Handled(${routePattern}, ${location})"
  }
  private[http] final case class Unhandled[Params, Input, -Env, +Err](
    rpm: RoutePatternMiddleware[Env, Params],
    handler: Handler[Env, Err, Input, Response],
    zippable: Zippable.Out[Params, Request, Input],
    location: Trace,
  ) extends Route[Env, Err] { self =>

    def routePattern = rpm.routePattern

    override def toHandler(implicit ev: Err <:< Response): Handler[Env, Response, Request, Response] = {
      implicit val z = zippable

      Route.handled(rpm)(handler.asErrorType[Response]).toHandler
    }

    override def toString() = s"Route.Unhandled(${routePattern}, ${location})"
  }
}
