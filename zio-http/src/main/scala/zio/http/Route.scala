package zio.http

import zio._

import zio.http.codec._

/**
 * Represents a single route, which has either handled its errors by converting
 * them into responses, or which has polymorphic errors.
 */
sealed trait Route[-Env, +Err] { self =>
  type PathInput

  /**
   * Applies the route to the specified request. The route must be defined for
   * the request, or else this method will fail fatally. Note that you may only
   * call this function when you have handled all errors produced by the route,
   * converting them into responses.
   */
  def apply(request: Request)(implicit ev: Err <:< Response): ZIO[Env, Response, Response]

  def asErrorType[Err2](implicit ev: Err <:< Err2): Route[Env, Err2] = self.asInstanceOf[Route[Env, Err2]]

  /**
   * Handles the error of the route. This method can be used to convert a route
   * that does not handle its errors into one that does handle its errors.
   */
  final def handleError(f: Err => Response): Route[Env, Nothing] =
    self match {
      case Route.Handled(routePattern, handler, l)       => Route.Handled(routePattern, handler, l)
      case r @ Route.Unhandled(routePattern, handler, l) =>
        Route.Handled(routePattern, (pi: r.PathInput) => handler(pi).mapError(f), l)
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
      case Route.Handled(routePattern, handler, l)       => Route.Handled(routePattern, handler, l)
      case r @ Route.Unhandled(routePattern, handler, l) =>
        Route.Unhandled(routePattern, (pi: r.PathInput) => handler(pi).mapError(f), l)
    }

  /**
   * The route pattern over which the route is defined. The route can only
   * handle requests that match this route pattern.
   */
  def routePattern: RoutePattern[PathInput]

  final def toApp(implicit ev: Err <:< Nothing): App[Env] = Routes(self).toApp
}
object Route                   {
  import zio.http.endpoint._

  def handled[PathInput, Env](routePattern: RoutePattern[PathInput]): HandledConstructor[PathInput] =
    new HandledConstructor[PathInput](routePattern)

  def route[PathInput, Env, Err](routePattern: RoutePattern[PathInput]): UnhandledConstructor[PathInput] =
    new UnhandledConstructor[PathInput](routePattern)

  final class HandledConstructor[PathInput](val routePattern: RoutePattern[PathInput]) extends AnyVal {
    def apply[Env](handler: Handler[Env, Response, Request, Response])(implicit trace: Trace): Route[Env, Nothing] =
      Handled(routePattern, (_: PathInput) => handler, trace)

    def apply[Env](handler: PathInput => Handler[Env, Response, Request, Response])(implicit
      trace: Trace,
    ): Route[Env, Nothing] =
      Handled(routePattern, handler, trace)
  }

  final class UnhandledConstructor[PathInput](val routePattern: RoutePattern[PathInput]) extends AnyVal {
    def apply[Env, Err](handler: Handler[Env, Err, Request, Response])(implicit trace: Trace): Route[Env, Err] =
      Unhandled(routePattern, (_: PathInput) => handler, trace)

    def apply[Env, Err](handler: PathInput => Handler[Env, Err, Request, Response])(implicit
      trace: Trace,
    ): Route[Env, Err] =
      Unhandled(routePattern, handler, trace)
  }

  private[http] final case class Handled[PI, -Env](
    routePattern: RoutePattern[PI],
    handler: PI => Handler[Env, Response, Request, Response],
    location: Trace,
  ) extends Route[Env, Nothing] {
    type PathInput = PI

    def apply(request: Request)(implicit ev: Nothing <:< Response): ZIO[Env, Response, Response] =
      routePattern.decode(request.method, request.path) match {
        case Right(pathInput) => handler(pathInput)(request)
        case Left(error)      =>
          ZIO.die(
            new MatchError(
              s"Route not defined for path ${request.method}: ${request.url.path}, cause: $error",
            ),
          )
      }

    override def toString() = s"Route.Handled(${routePattern}, ${location})"
  }
  private[http] final case class Unhandled[PI, -Env, +Err](
    routePattern: RoutePattern[PI],
    handler: PI => Handler[Env, Err, Request, Response],
    location: Trace,
  ) extends Route[Env, Err] {
    type PathInput = PI

    def apply(request: Request)(implicit ev: Err <:< Response): ZIO[Env, Response, Response] =
      routePattern.decode(request.method, request.path) match {
        case Right(pathInput) => handler(pathInput)(request).mapError(ev)
        case Left(error)      =>
          ZIO.die(
            new NoSuchElementException(
              s"Route not defined for path ${request.method}: ${request.url.path}, cause: $error",
            ),
          )
      }

    override def toString() = s"Route.Unhandled(${routePattern}, ${location})"
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
