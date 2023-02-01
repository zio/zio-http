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
final case class EndpointExecutor[ME, MI](
  client: Client,
  locator: EndpointLocator,
  middlewareInput: IO[ME, MI],
  trace: Trace
) {
  val metadata = {
    implicit val trace0 = trace 
      zio.http.api.internal.MemoizedZIO[Endpoint[_, _, _, _ <: EndpointMiddleware], EndpointError, EndpointClient[Any, Any, Any, _]] {
      (api: Endpoint[_, _, _, _ <: EndpointMiddleware]) =>
        locator.locate(api).map { location => 
          EndpointClient(
            location,
            api.asInstanceOf[Endpoint[Any, Any, Any, _ <: EndpointMiddleware]],
          )
      }
    }
  }

  def apply[A, E, B](
      invocation: Invocation[A, E, B, _ <: EndpointMiddleware],
    )(implicit alt: Alternator[E, ME], trace: Trace): ZIO[Any, alt.Out, B] = {
    metadata.get(invocation.api).orDie.flatMap { executor =>
      executor.execute(client, invocation.input).asInstanceOf[ZIO[Any, alt.Out, B]]
    }
  }
}
