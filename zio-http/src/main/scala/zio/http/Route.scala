package zio.http

import zio._

import zio.http.codec._

/**
 * Represents a single route, which may be either static or dynamic. Static
 * routes have known paths.
 */
sealed trait Route[-Env, +Err] {
  type PathInput

  def routePattern: RoutePattern[PathInput]

  def apply(request: Request)(implicit ev: Err <:< Response): ZIO[Env, Response, Response]

  final def isDefinedAt(request: Request): Boolean = routePattern.matches(request.method, request.path)
}
object Route                   {
  import zio.http.endpoint._

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

  final case class Handled[PI, -Env](
    routePattern: RoutePattern[PI],
    handler: PI => Handler[Env, Response, Request, Response],
  ) extends Route[Env, Nothing] {
    type PathInput = PI

    def apply(request: Request)(implicit ev: Nothing <:< Response): ZIO[Env, Response, Response] =
      routePattern.decode(request.method, request.path) match {
        case Right(pathInput) => handler(pathInput)(request)
        case Left(error)      =>
          ZIO.die(
            new NoSuchElementException(
              s"Route not defined for path ${request.method}: ${request.url.path}, cause: $error",
            ),
          )
      }

  }
  final case class Unhandled[PI, -Env, +Err](
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
}
