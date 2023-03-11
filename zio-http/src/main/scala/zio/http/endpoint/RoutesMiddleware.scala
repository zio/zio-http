package zio.http.endpoint

import zio._

import zio.http._

/**
 * A [[RoutesMiddleware]] defines the middleware implementation for a given
 * [[EndpointMiddleware]]. These middleware are defined by a pair of functions,
 * referred to as _incoming interceptor_ and _outgoing interceptor_, which are
 * applied to incoming requests and outgoing responses.
 */
trait RoutesMiddleware[-R, S, +M <: EndpointMiddleware] {
  val middleware: M

  final type E = middleware.Err
  final type I = middleware.In
  final type O = middleware.Out

  /**
   * The incoming interceptor is responsible for taking the input to the
   * middleware, derived from the request according to the definition of the
   * middleware, and either failing, or producing a state value, which will be
   * passed with the outgoing interceptor.
   */
  def incoming(input: I): ZIO[R, E, S]

  /**
   * The outgoing interceptor is responsible for taking the state value produced
   * by the incoming interceptor, and either failing, or producing an output
   * value, which will be used to patch the response.
   */
  def outgoing(state: S): ZIO[R, E, O]

  /**
   * Converts this [[RoutesMiddleware]] to a [[zio.http.HandlerAspect]], which
   * can be applied in straightforward fashion to any request handler or HTTP.
   */
  final def toHandlerAspect: HandlerAspect[Nothing, R, Nothing, Any] =
    new HandlerAspect.Simple[Nothing, R, Nothing, Any] {
      def apply[R1 >: Nothing <: R, E1 >: Nothing <: Any](handler: Handler[R1, E1, Request, Response])(implicit
        trace: Trace,
      ): Handler[R1, E1, Request, Response] = {
        Handler.fromFunctionZIO[Request] { request =>
          decodeMiddlewareInput(request).flatMap { input =>
            incoming(input).foldZIO(
              e => ZIO.succeed(encodeMiddlewareError(e)),
              { state =>
                handler(request).flatMap { response =>
                  outgoing(state).fold(
                    encodeMiddlewareError(_),
                    { output =>
                      response.patch(encodeMiddlewareOutput(output))
                    },
                  )
                }
              },
            )
          }
        }
      }
    }

  private def decodeMiddlewareInput(request: Request): ZIO[R, Nothing, I] =
    middleware.input.decodeRequest(request).orDie

  private def encodeMiddlewareOutput(output: O): Response.Patch =
    middleware.output.encodeResponsePatch(output)

  private def encodeMiddlewareError(error: E): Response =
    middleware.error.encodeResponse(error)
}
object RoutesMiddleware                                 {

  /**
   * A [[RoutesMiddleware]] that does nothing.
   */
  val none: RoutesMiddleware[Any, Unit, EndpointMiddleware.None] =
    EndpointMiddleware.none.implement(_ => ZIO.unit)(_ => ZIO.unit)

  /**
   * Constructs a new [[RoutesMiddleware]] from both the definition of the
   * middleware, together with a pair of incoming and outgoing interceptors.
   */
  def make[M <: EndpointMiddleware](
    middleware: M,
  ): Apply[M] = new Apply[M](middleware)

  final class Apply[M <: EndpointMiddleware](val m: M) extends AnyVal {
    def apply[R, S](
      incoming0: m.In => ZIO[R, m.Err, S],
    )(outgoing0: S => ZIO[R, m.Err, m.Out]): RoutesMiddleware[R, S, m.type] =
      new RoutesMiddleware[R, S, m.type] {
        val middleware: m.type = m

        def incoming(input: I): ZIO[R, m.Err, S] = incoming0(input)

        def outgoing(state: S): ZIO[R, m.Err, O] = outgoing0(state)
      }
  }
}
