package zio.http

import zio._

import zio.http.codec._

/**
 * Represents a single route, which has either handled its errors by converting
 * them into responses, or which has polymorphic errors.
 */
sealed trait Route[-Env, +Err] { self =>
  type PathInput

  def routePattern: RoutePattern[PathInput]

  def apply(request: Request)(implicit ev: Err <:< Response): ZIO[Env, Response, Response]

  final def isDefinedAt(request: Request): Boolean = routePattern.matches(request.method, request.path)

  final def mapError[Err2](f: Err => Err2): Route[Env, Err2] =
    self match {
      case Route.Handled(routePattern, handler)       => Route.Handled(routePattern, handler)
      case r @ Route.Unhandled(routePattern, handler) =>
        Route.Unhandled(routePattern, (pi: r.PathInput) => handler(pi).mapError(f))
    }
}
object Route                   {
  import zio.http.endpoint._

  def handled[PathInput, Env](
    routePattern: RoutePattern[PathInput],
    handler: PathInput => Handler[Env, Response, Request, Response],
  ): Route[Env, Nothing] =
    Handled(routePattern, handler)

  def unhandled[PathInput, Env, Err](
    routePattern: RoutePattern[PathInput],
    handler: PathInput => Handler[Env, Err, Request, Response],
  ): Route[Env, Err] =
    Unhandled(routePattern, handler)

  def fromEndpoint[Env, Err, P, In, Out, M <: EndpointMiddleware](
    endpoint: Endpoint[P, In, Err, Out, M],
  )(original: Handler[Env, Err, In, Out]): Route[Env, Err] = {
    val handler = Handler.fromFunctionZIO { (request: Request) =>
      endpoint.input.decodeRequest(request).orDie.flatMap { value =>
        original(value).map(endpoint.output.encodeResponse(_)).catchAll { error =>
          ZIO.succeed(endpoint.error.encodeResponse(error))
        }
      }
    }

    Handled(endpoint.route, (_: P) => handler)
  }

  private[http] final case class Handled[PI, -Env](
    routePattern: RoutePattern[PI],
    handler: PI => Handler[Env, Response, Request, Response],
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

  }
  private[http] final case class Unhandled[PI, -Env, +Err](
    routePattern: RoutePattern[PI],
    handler: PI => Handler[Env, Err, Request, Response],
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
  }

  private[http] final case class Tree[-Env, +Err](tree: RoutePattern.Tree[Route[Env, Err]]) { self =>
    final def ++[Env1 <: Env, Err1 >: Err](that: Tree[Env1, Err1]): Tree[Env1, Err1] =
      Tree(self.tree ++ that.tree)

    final def add[Env1 <: Env, Err1 >: Err](route: Route[Env1, Err1]): Tree[Env1, Err1] =
      Tree(self.tree.add(route.routePattern, route))

    final def addAll[Env1 <: Env, Err1 >: Err](routes: Iterable[Route[Env1, Err1]]): Tree[Env1, Err1] =
      Tree(self.tree.addAll(routes.map(r => (r.routePattern, r))))

    final def get(method: Method, path: Path): Option[Route[Env, Err]] = self.get(method, path)
  }
  private[http] object Tree                                                                 {
    def apply[Env, Err](first: Route[Env, Err], rest: Route[Env, Err]*): Tree[Env, Err] =
      Tree.fromIterable(Chunk(first) ++ Chunk.fromIterable(rest))

    val empty: Tree[Any, Nothing] = Tree(RoutePattern.Tree.empty)

    def fromIterable[Env, Err](routes: Iterable[Route[Env, Err]]): Tree[Env, Err] =
      Tree.empty.addAll(routes)
  }
}
