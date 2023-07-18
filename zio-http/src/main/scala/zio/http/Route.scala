package zio.http

import zio._

/**
 * Represents a single route, which has either handled its errors by converting
 * them into responses, or which has polymorphic errors, which must later be
 * converted into responses before the route can be executed.
 */
sealed trait Route[-Env, +Err] { self =>

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
      cause.failureOption.map(f).getOrElse(Response(status = Status.InternalServerError))
    }

  final def handleErrorCause(f: Cause[Err] => Response): Route[Env, Nothing] =
    Route.HandleErrorCause(self, f)

  /**
   * Returns a route that ignores any errors produced by this one, translating
   * them into internal server errors that have no details. In order to provide
   * useful feedback to users of the API, it's a good idea to capture some
   * information on the failure and embed it into the response.
   */
  final def ignore: Route[Env, Nothing] =
    handleError {
      case t: Throwable => Response.fromThrowable(t)
      case e            => Response.internalServerError(e.toString())
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

  def toHandler(f: Cause[Err] => Response): Handler[Env, Response, Request, Response]

  def toHandler(implicit ev: Err <:< Response): Handler[Env, Response, Request, Response] = {
    val defaultErrorHandler: Cause[Err] => Response =
      cause =>
        cause.map(ev).failureOption match {
          case None           => Response(status = Status.InternalServerError)
          case Some(response) => response
        }

    toHandler(defaultErrorHandler)
  }

  final def toHttpApp(implicit ev: Err <:< Response): HttpApp[Env] = Routes(self).toHttpApp
}
object Route {

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

  private[http] final case class HandleErrorCause[-Env, Err](
    route: Route[Env, Err],
    errorHandler: Cause[Err] => Response,
  ) extends Route[Env, Nothing] {
    def location: Trace = route.location

    def routePattern: RoutePattern[_] = route.routePattern

    def toHandler(f0: Cause[Nothing] => Response): Handler[Env, Response, Request, Response] =
      route.toHandler(errorHandler)

    override def toString() = s"Route.HandleErrorCause(${route}, <function1>)"
  }

  private[http] final case class Provide[Env, +Err](
    route: Route[Env, Err],
    env: ZEnvironment[Env],
  ) extends Route[Any, Err] {
    def location: Trace = route.location

    def routePattern: RoutePattern[_] = route.routePattern

    def toHandler(f: Cause[Err] => Response): Handler[Any, Response, Request, Response] =
      route.toHandler(f).provideEnvironment(env)

    override def toString() = s"Route.Provide(${route}, ${env})"
  }

  private[http] final case class Augmented[-Env, +Err](
    route: Route[Env, Err],
    aspect: RouteAspect[Nothing, Env],
  ) extends Route[Env, Err] {
    def location: Trace = route.location

    def routePattern: RoutePattern[_] = route.routePattern

    def toHandler(f: Cause[Err] => Response): Handler[Env, Response, Request, Response] =
      aspect(route.toHandler(f))

    override def toString() = s"Route.Augmented(${route}, ${aspect})"
  }

  private[http] final case class Handled[-Env](
    routePattern: RoutePattern[_],
    handler: Handler[Env, Response, Request, Response],
    location: Trace,
  ) extends Route[Env, Nothing] {
    def toHandler(f: Cause[Nothing] => Response): Handler[Env, Response, Request, Response] =
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

    def toHandler(f: Cause[Err] => Response): Handler[Env, Response, Request, Response] = {
      implicit val z = zippable

      Route.handled(rpm)(handler.catchAllCause(cause => Handler.fail(f(cause)))).toHandler(f)
    }

    override def toString() = s"Route.Unhandled(${routePattern}, ${location})"
  }
}
