package zio.http.api

import zio._
import zio.http._
import zio.http.api.internal.EndpointClient
import zio.stacktracer.TracingImplicits.disableAutoTrace // scalafix:ok;

/**
 * A [[zio.http.api.EndpointExecutor]] is responsible for taking an endpoint
 * invocation, and executing this invocation, returning the final result, or
 * failing with some kind of RPC error.
 */
final case class EndpointExecutor[MI](
  client: Client,
  locator: EndpointLocator,
  middlewareInput: UIO[MI],
) {
  val metadata = {
    implicit val trace0 = Trace.empty
    zio.http.api.internal
      .MemoizedZIO[Endpoint[_, _, _, _ <: EndpointMiddleware], EndpointError, EndpointClient[Any, Any, Any, _]] {
        (api: Endpoint[_, _, _, _ <: EndpointMiddleware]) =>
          locator.locate(api).map { location =>
            EndpointClient(
              location,
              api.asInstanceOf[Endpoint[Any, Any, Any, _ <: EndpointMiddleware]],
            )
          }
      }
  }

  def getClient[I, E, O, M <: EndpointMiddleware](
    endpoint: Endpoint[I, E, O, M],
  )(implicit trace: Trace): IO[EndpointError, EndpointClient[I, E, O, M]] =
    metadata.get(endpoint).map(_.asInstanceOf[EndpointClient[I, E, O, M]])

  def apply[A, E, B, M <: EndpointMiddleware](
    invocation: Invocation[A, E, B, M],
  )(implicit
    alt: Alternator[E, invocation.middleware.Err],
    ev: MI <:< invocation.middleware.In,
    trace: Trace,
  ): ZIO[Any, alt.Out, B] = {
    middlewareInput.flatMap { mi =>
      getClient(invocation.endpoint).orDie.flatMap { endpointClient =>
        endpointClient.execute(client, invocation)(ev(mi))
      }
    }
  }
}
