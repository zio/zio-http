package zio.http

import zio._

import zio.http.codec._

/**
 * Represents a single route, which may be either static or dynamic. Static
 * routes have known paths.
 */
sealed trait Route[-Env] extends PartialFunction[Request, ZIO[Env, Response, Response]] {
  def handler: Handler[Env, Response, Request, Response]

  def apply(request: Request): ZIO[Env, Response, Response] = handler(request)

  def isDefinedAt(request: Request): Boolean = this match {
    case Route.Dynamic(lookup) => lookup.isDefinedAt(request)
    case _                     => ???
  }

}
object Route {
  import zio.http.endpoint._

  private[http] sealed trait Static[-Env, Err] extends Route[Env] {
    type Input
    type Output
    type Middleware <: EndpointMiddleware

    def endpoint: Endpoint[Input, Err, Output, Middleware]
    def original: Handler[Env, Err, Input, Output]

    val handler: Handler[Env, Response, Request, Response] =
      Handler.fromFunctionZIO { request =>
        endpoint.input.decodeRequest(request).orDie.flatMap { value =>
          original(value).map(endpoint.output.encodeResponse(_)).catchAll { error =>
            ZIO.succeed(endpoint.error.encodeResponse(error))
          }
        }
      }
  }
  private[http] final case class Dynamic[Env](
    lookup: PartialFunction[Request, Handler[Env, Response, Request, Response]],
  ) extends Route[Env] {
    val handler: Handler[Env, Response, Request, Response] =
      Handler.fromFunctionZIO { request =>
        lookup.lift(request) match {
          case Some(handler) => handler(request)
          case None          =>
            ZIO.die(new NoSuchElementException(s"Route not defined for path ${request.method}: ${request.url.path}"))
        }
      }
  }

  def collect[Env, Err](pf: PartialFunction[Request, Handler[Env, Response, Request, Response]]): Route[Env] = Dynamic(
    pf,
  )

  def apply[Env, Err, In, Out, M <: EndpointMiddleware](
    endpoint0: Endpoint[In, Err, Out, M],
  )(handler0: Handler[Env, Err, In, Out]): Route[Env] =
    new Static[Env, Err] {
      type Input      = In
      type Output     = Out
      type Middleware = M

      def endpoint: Endpoint[Input, Err, Output, Middleware] = endpoint0
      def original: Handler[Env, Err, Input, Output]         = handler0
    }
}
