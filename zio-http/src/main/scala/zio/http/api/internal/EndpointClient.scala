package zio.http.api.internal

import zio._
import zio.http._
import zio.http.api._
import zio.http.model.Headers
import zio.schema._
import zio.schema.codec._
import zio.stacktracer.TracingImplicits.disableAutoTrace // scalafix:ok;

private[api] final case class EndpointClient[I, E, O, M <: EndpointMiddleware](
  endpointRoot: URL,
  endpoint: Endpoint[I, E, O, M],
) {
  def execute(client: Client, invocation: Invocation[I, E, O, M])(
    mi: invocation.middleware.In,
  )(implicit alt: Alternator[E, invocation.middleware.Err], trace: Trace): ZIO[Any, alt.Out, O] = {
    val request0 = endpoint.input.encodeRequest(invocation.input)
    val request  = request0.copy(url = endpointRoot ++ request0.url)

    val requestPatch = invocation.middleware.input.encodeRequestPatch(mi)

    client.request(request.patch(requestPatch)).orDie.flatMap { response =>
      if (response.status.isSuccess) {
        endpoint.output.decodeResponse(response).orDie
      } else {
        // Preferentially decode an error from the handler, before falling back
        // to decoding the middleware error:
        val leftError =
          endpoint.error.decodeResponse(response).orDie.flatMap((e: E) => ZIO.fail(alt.left(e)))

        val rightError =
          invocation.middleware.error
            .decodeResponse(response)
            .orDie
            .flatMap((e: invocation.middleware.Err) => ZIO.fail(alt.right(e)))

        leftError.orElse(rightError)
      }
    }
  }
}
