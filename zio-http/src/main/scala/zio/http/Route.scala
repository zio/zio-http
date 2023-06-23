package zio.http

import zio._

import zio.http.codec._

/**
 * Represents a single route, which may be either static or dynamic. Static
 * routes have known paths.
 */
sealed trait Route[-Env, +Err] {
  // type PathInput

  // def path: RoutePattern[PathInput]

  def handler(implicit ev: Err <:< Response): Handler[Env, Response, Request, Response]

  def apply(request: Request)(implicit ev: Err <:< Response): ZIO[Env, Response, Response] =
    handler(ev)(request)

  def isDefinedAt(request: Request): Boolean = this match {
    case Route.Dynamic(lookup) => lookup.isDefinedAt(request)
    case _                     => ???
  }

}
object Route {
  import zio.http.endpoint._

  private[http] sealed trait Static[-Env] extends Route[Env, Nothing] {
    type PathInput
    type Input
    type Output
    type Error
    type Middleware <: EndpointMiddleware

    def endpoint: Endpoint[PathInput, Input, Error, Output, Middleware]
    def original: Handler[Env, Error, Input, Output]

    def handler(implicit ev: Nothing <:< Response): Handler[Env, Response, Request, Response] =
      Handler.fromFunctionZIO { request =>
        endpoint.input.decodeRequest(request).orDie.flatMap { value =>
          original(value).map(endpoint.output.encodeResponse(_)).catchAll { error =>
            ZIO.succeed(endpoint.error.encodeResponse(error))
          }
        }
      }
  }
  private[http] final case class Dynamic[Env, Err](
    lookup: PartialFunction[Request, Handler[Env, Err, Request, Response]],
  ) extends Route[Env, Err] {
    def handler(implicit ev: Err <:< Response): Handler[Env, Response, Request, Response] =
      Handler.fromFunctionZIO { request =>
        lookup.lift(request) match {
          case Some(handler) => handler(request).mapError(ev)
          case None          =>
            ZIO.die(new NoSuchElementException(s"Route not defined for path ${request.method}: ${request.url.path}"))
        }
      }
  }

  def collect[Env, Err](pf: PartialFunction[Request, Handler[Env, Err, Request, Response]]): Route[Env, Err] = Dynamic(
    pf,
  )

  def apply[Env, Err, P, In, Out, M <: EndpointMiddleware](
    endpoint0: Endpoint[P, In, Err, Out, M],
  )(handler0: Handler[Env, Err, In, Out]): Route[Env, Nothing] =
    new Static[Env] {
      type PathInput  = P
      type Input      = In
      type Output     = Out
      type Middleware = M
      type Error      = Err

      def endpoint: Endpoint[PathInput, Input, Err, Output, Middleware] = endpoint0
      def original: Handler[Env, Err, Input, Output]                    = handler0
    }
}
