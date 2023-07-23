package zio.http

import zio._

import zio.http.Route.Provided

/**
 * Represents a single route, which has either handled its errors by converting
 * them into responses, or which has polymorphic errors, which must later be
 * converted into responses before the route can be executed.
 *
 * Routes have the property that, before conversion into handlers, they will
 * fully handle all errors, including defects, translating them appropriately
 * into responses that can be delivered to clients. Thus, the handlers returned
 * by `toHandler` will never fail, and will always produce a valid response.
 *
 * Individual routes can be aggregated using [[ziop.http.Routes]].
 */
sealed trait Route[-Env, +Err] { self =>
  import Route.{Augmented, Handled, Provided, Unhandled}

  /**
   * Augments this route with the specified middleware.
   */
  def @@[Env1 <: Env](
    aspect: Handler[Env1, Response, Request, Response] => Handler[Env1, Response, Request, Response],
  ): Route[Env1, Err] =
    Route.Augmented[Env1, Err](self, aspect)

  /**
   * Applies the route to the specified request. The route must be defined for
   * the request, or else this method will fail fatally. Note that you may only
   * call this function when you have handled all errors produced by the route,
   * converting them into responses.
   */
  final def apply(request: Request)(implicit ev: Err <:< Response): ZIO[Env, Response, Response] =
    toHandler.apply(request)

  def asErrorType[Err2](implicit ev: Err <:< Err2): Route[Env, Err2] = self.asInstanceOf[Route[Env, Err2]]

  def augmented[Env1 <: Env](
    f: Handler[Env1, Response, Request, Response] => Handler[Env1, Response, Request, Response],
  ): Route[Env1, Err] =
    Route.Augmented(self, f)

  /**
   * Handles the error of the route. This method can be used to convert a route
   * that does not handle its errors into one that does handle its errors.
   */
  final def handleError(f: Err => Response): Route[Env, Nothing] =
    self.handleErrorCause(Response.fromCauseWith(_)(f))

  /**
   * Handles the error of the route. This method can be used to convert a route
   * that does not handle its errors into one that does handle its errors.
   */
  final def handleErrorCause(f: Cause[Err] => Response): Route[Env, Nothing] =
    self match {
      case Provided(route, env)                     => Provided(route.handleErrorCause(f), env)
      case Augmented(route, aspect)                 => Augmented(route.handleErrorCause(f), aspect)
      case Handled(routePattern, handler, location) => Handled(routePattern, handler, location)

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

          // Sandbox before applying middleware:
          rpm.middleware.applyHandlerContext(paramHandler.mapErrorCause(f))
        }

        Handled(rpm.routePattern, handler2, location)
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

  final def provideEnvironment(env: ZEnvironment[Env]): Route[Any, Err] =
    Route.Provided(self, env)

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

  def handled[Env](
    routePattern: RoutePattern[_],
  )(handler: Handler[Env, Response, Request, Response])(implicit trace: Trace): Route[Env, Nothing] = {
    // Sandbox before constructing:
    Route.Handled(routePattern, handler.sandbox, Trace.empty)
  }

  def handled[Params, Env](rpm: RoutePatternMiddleware[Env, Params]): HandledConstructor[Env, Params] =
    new HandledConstructor[Env, Params](rpm)

  val notFound: Route[Any, Nothing] =
    Handled(RoutePattern.any, Handler.notFound, Trace.empty)

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

        // Sandbox before applying middleware:
        rpm.middleware.applyHandlerContext(paramHandler.sandbox)
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

  private final case class Provided[Env, +Err](
    route: Route[Env, Err],
    env: ZEnvironment[Env],
  ) extends Route[Any, Err] {
    def location: Trace = route.location

    def routePattern: RoutePattern[_] = route.routePattern

    override def toHandler(implicit ev: Err <:< Response): Handler[Any, Response, Request, Response] =
      route.toHandler.provideEnvironment(env)

    override def toString() = s"Route.Provided(${route}, ${env})"
  }

  private final case class Augmented[Env, +Err](
    route: Route[Env, Err],
    aspect: Handler[Env, Response, Request, Response] => Handler[Env, Response, Request, Response],
  ) extends Route[Env, Err] {
    def location: Trace = route.location

    def routePattern: RoutePattern[_] = route.routePattern

    override def toHandler(implicit ev: Err <:< Response): Handler[Env, Response, Request, Response] =
      aspect(route.toHandler)

    override def toString() = s"Route.Augmented(${route}, ${aspect})"
  }

  private final case class Handled[-Env](
    routePattern: RoutePattern[_],
    handler: Handler[Env, Response, Request, Response],
    location: Trace,
  ) extends Route[Env, Nothing] {
    override def toHandler(implicit ev: Nothing <:< Response): Handler[Env, Response, Request, Response] =
      handler

    override def toString() = s"Route.Handled(${routePattern}, ${location})"
  }
  private final case class Unhandled[Params, Input, -Env, +Err](
    rpm: RoutePatternMiddleware[Env, Params],
    handler: Handler[Env, Err, Input, Response],
    zippable: Zippable.Out[Params, Request, Input],
    location: Trace,
  ) extends Route[Env, Err] { self =>

    def routePattern = rpm.routePattern

    override def toHandler(implicit ev: Err <:< Response): Handler[Env, Response, Request, Response] = {
      convert(handler.asErrorType[Response])
    }

    override def toString() = s"Route.Unhandled(${routePattern}, ${location})"

    private def convert[Env1 <: Env](
      handler: Handler[Env1, Response, Input, Response],
    ): Handler[Env1, Response, Request, Response] = {
      implicit val z = zippable

      Route.handled(rpm)(handler).toHandler
    }
  }
}
