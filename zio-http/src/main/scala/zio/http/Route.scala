package zio.http

import zio._

/**
 * Represents a single route, which has either handled its errors by converting
 * them into responses, or which has polymorphic errors, which must later be
 * converted into responses before the route can be executed.
 */
sealed trait Route[-Env, +Err] { self =>

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
    self match {
      case Route.Handled(r, h, l)      => Route.Handled(r, h, l)
      case Route.Unhandled(r, h, z, l) => Route.handled(r)(h.mapError(f))(z, l)
    }

  /**
   * Returns a route that ignores any errors produced by this one, translating
   * them into internal server errors that have no details. In order to provide
   * useful feedback to users of the API, it's a good idea to capture some
   * information on the failure and embed it into the response.
   */
  final def ignoreErrors: Route[Env, Nothing] =
    handleError(_ => Response(status = Status.InternalServerError))

  /**
   * Determines if the route is defined for the specified request.
   */
  final def isDefinedAt(request: Request): Boolean = routePattern.matches(request.method, request.path)

  def location: Trace

  /**
   * Changes the error type of the route, without eliminating it.
   */
  final def mapError[Err2](f: Err => Err2): Route[Env, Err2] =
    self match {
      case Route.Handled(r, h, l)      => Route.Handled(r, h, l)
      case Route.Unhandled(r, h, z, l) => Route.Unhandled(r, h.mapError(f), z, l)
    }

  /**
   * The route pattern over which the route is defined. The route can only
   * handle requests that match this route pattern.
   */
  def routePattern: RoutePattern[_]

  def toHandler(implicit ev: Err <:< Response): Handler[Env, Response, Request, Response]

  final def toApp(implicit ev: Err <:< Nothing): App[Env] = Routes(self).toApp
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
          Handler.fromFunctionZIO[(Request, rpm.Context)] { case (request, ctx) =>
            rpm.routePattern.decode(request.method, request.path) match {
              case Left(error)  => ZIO.dieMessage(error)
              case Right(value) =>
                val params = rpm.zippable.zip(value, ctx)

                handler(zippable.zip(params, request))
            }
          }

        rpm.middleware(paramHandler)
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

  private[http] final case class Handled[-Env](
    routePattern: RoutePattern[_],
    handler: Handler[Env, Response, Request, Response],
    location: Trace,
  ) extends Route[Env, Nothing] {
    def toHandler(implicit ev: Nothing <:< Response): Handler[Env, Response, Request, Response] = handler

    override def toString() = s"Route.Handled(${routePattern}, ${location})"
  }
  private[http] final case class Unhandled[Params, Input, -Env, +Err](
    rpm: RoutePatternMiddleware[Env, Params],
    handler: Handler[Env, Err, Input, Response],
    zippable: Zippable.Out[Params, Request, Input],
    location: Trace,
  ) extends Route[Env, Err] { self =>
    def routePattern = rpm.routePattern

    def toHandler(implicit ev: Err <:< Response): Handler[Env, Response, Request, Response] =
      convert(self.handleError(ev))

    override def toString() = s"Route.Unhandled(${routePattern}, ${location})"

    // Workaround:
    private def convert[Env1 <: Env](handled: Route[Env1, Nothing]): Handler[Env1, Response, Request, Response] =
      handled.toHandler
  }

  private[http] final case class Tree[-Env, +Err](tree: RoutePattern.Tree[Route[Env, Err]]) { self =>
    final def ++[Env1 <: Env, Err1 >: Err](that: Tree[Env1, Err1]): Tree[Env1, Err1] =
      Tree(self.tree ++ that.tree)

    final def add[Env1 <: Env, Err1 >: Err](route: Route[Env1, Err1]): Tree[Env1, Err1] =
      Tree(self.tree.add(route.routePattern, route))

    final def addAll[Env1 <: Env, Err1 >: Err](routes: Iterable[Route[Env1, Err1]]): Tree[Env1, Err1] =
      Tree(self.tree.addAll(routes.map(r => (r.routePattern, r))))

    final def get(method: Method, path: Path): Chunk[Route[Env, Err]] = tree.get(method, path)
  }
  private[http] object Tree                                                                 {
    def apply[Env, Err](first: Route[Env, Err], rest: Route[Env, Err]*): Tree[Env, Err] =
      Tree.fromIterable(Chunk(first) ++ Chunk.fromIterable(rest))

    val empty: Tree[Any, Nothing] = Tree(RoutePattern.Tree.empty)

    def fromIterable[Env, Err](routes: Iterable[Route[Env, Err]]): Tree[Env, Err] =
      Tree.empty.addAll(routes)
  }
}
