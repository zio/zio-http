package zio.http

import zio._

/**
 * Represents a table of routes, which are defined by pairs of route patterns
 * and route handlers.
 */
final class Routes[-Env, +Err] private (val routes: Chunk[zio.http.Route[Env, Err]]) { self =>
  def ++[Env1 <: Env, Err1 >: Err](that: Routes[Env1, Err1]): Routes[Env1, Err1] =
    new Routes(self.routes ++ that.routes)

  def :+[Env1 <: Env, Err1 >: Err](route: zio.http.Route[Env1, Err1]): Routes[Env1, Err1] =
    new Routes(routes :+ route)

  def +:[Env1 <: Env, Err1 >: Err](route: zio.http.Route[Env1, Err1]): Routes[Env1, Err1] =
    new Routes(route +: routes)

  /**
   * Looks up the route for the specified method and path.
   */
  def get(method: Method, path: Path): Chunk[zio.http.Route[Env, Err]] =
    tree.get(method, path)

  /**
   * Handles all typed errors in the routes by converting them into responses.
   */
  def handleError(f: Err => Response): Routes[Env, Nothing] =
    new Routes(routes.map(_.handleError(f)))

  def ignoreErrors: Routes[Env, Nothing] =
    new Routes(routes.map(_.ignoreErrors))

  def isDefinedAt(method: Method, path: Path): Boolean = tree.get(method, path).nonEmpty

  /**
   * Maps unhandled errors across all routes into a new type, without
   * eliminating them.
   */
  def mapError[Err2](f: Err => Err2): Routes[Env, Err2] =
    new Routes(routes.map(_.mapError(f)))

  // FIXME: Temporary stopgap until the final refactor.
  def toApp(implicit ev: Err <:< Nothing): App[Env] =
    Http.collectZIO[Request] {
      case request if isDefinedAt(request.method, request.path) =>
        get(request.method, request.path).head.apply(request)
    }

  private var _tree: Route.Tree[Any, Any] = null.asInstanceOf[Route.Tree[Any, Any]]

  // Avoid overhead of lazy val:
  private def tree: Route.Tree[Env, Err] = {
    if (_tree eq null) _tree = Route.Tree.fromIterable(routes).asInstanceOf[Route.Tree[Any, Any]]
    _tree.asInstanceOf[Route.Tree[Env, Err]]
  }
}
object Routes                                                                        {

  /**
   * Constructs new routes from a varargs of individual routes.
   */
  def apply[Env, Err](route: zio.http.Route[Env, Err], routes: zio.http.Route[Env, Err]*): Routes[Env, Err] =
    new Routes(Chunk(route) ++ Chunk.fromIterable(routes))

  /**
   * Constructs new routes from an iterable of individual routes.
   */
  def fromIterable[Env, Err](iterable: Iterable[Route[Env, Err]]): Routes[Env, Err] =
    new Routes(Chunk.fromIterable(iterable))

  def singleton[Env, Err](h: Handler[Env, Err, (Path, Request), Response]): Routes[Env, Err] =
    Routes(Route.route(RoutePattern.any)(h))

  def singletonZIO[Env, Err](f: (Path, Request) => ZIO[Env, Err, Response]): Routes[Env, Err] =
    singleton(Handler.fromFunctionZIO(f.tupled))
}
