package zio.http

import zio._

/**
 * Represents a collection of routes.
 */
final case class Routes2[-Env, +Err](routes: Chunk[zio.http.Route[Env, Err]]) { self =>
  final def ++[Env1 <: Env, Err1 >: Err](that: Routes2[Env1, Err1]): Routes2[Env1, Err1] =
    Routes2(self.routes ++ that.routes)

  /**
   * Looks up the route for the specified method and path by using an efficient
   * prefix tree.
   */
  def get(method: Method, path: Path): Option[zio.http.Route[Env, Err]] = tree.get(method, path)

  private var _tree: Route.Tree[Any, Any] = null.asInstanceOf[Route.Tree[Any, Any]]

  // Avoid overhead of lazy val:
  private def tree: Route.Tree[Env, Err] = {
    if (_tree eq null) _tree = Route.Tree.fromIterable(routes).asInstanceOf[Route.Tree[Any, Any]]
    _tree.asInstanceOf[Route.Tree[Env, Err]]
  }
}
object Routes2                                                                {
  def apply[Env, Err](route: zio.http.Route[Env, Err], routes: zio.http.Route[Env, Err]*): Routes2[Env, Err] =
    Routes2(Chunk(route) ++ Chunk.fromIterable(routes))
}
