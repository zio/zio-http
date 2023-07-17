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
   * Augments this collection of routes with the specified middleware.
   */
  def @@[Env1 <: Env](aspect: RouteAspect[Nothing, Env1]): Routes[Env1, Err] =
    new Routes(routes.map(_.@@(aspect)))

  /**
   * Handles all typed errors in the routes by converting them into responses.
   */
  def handleError(f: Err => Response): Routes[Env, Nothing] =
    new Routes(routes.map(_.handleError(f)))

  def handleErrorCause(f: Cause[Err] => Response): Routes[Env, Nothing] =
    new Routes(routes.map(_.handleErrorCause(f)))

  /**
   * Ignores errors, turning them into internal server errors.
   */
  def ignore: Routes[Env, Nothing] =
    new Routes(routes.map(_.ignore))

  def provideEnvironment(env: ZEnvironment[Env]): Routes[Any, Err] =
    new Routes(routes.map(_.provideEnvironment(env)))

  def timeout(duration: Duration): Routes[Env, Err] =
    self @@ RouteAspect.timeout(duration)

  /**
   * Converts the routes into an app, which can be done only when errors are
   * handled and converted into responses.
   */
  def toApp(implicit ev: Err <:< Response): HttpApp2[Env] =
    HttpApp2(routes.asInstanceOf[Routes[Env, Response]])
}
object Routes                                                                        {

  /**
   * Constructs new routes from a varargs of individual routes.
   */
  def apply[Env, Err](route: zio.http.Route[Env, Err], routes: zio.http.Route[Env, Err]*): Routes[Env, Err] =
    new Routes(Chunk(route) ++ Chunk.fromIterable(routes))

  val empty: Routes[Any, Nothing] = new Routes(Chunk.empty)

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
