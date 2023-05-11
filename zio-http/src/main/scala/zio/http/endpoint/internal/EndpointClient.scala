/*
 * Copyright 2021 - 2023 Sporta Technologies PVT LTD & the ZIO HTTP contributors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package zio.http.endpoint.internal

import zio._

import zio.http._
import zio.http.codec._
import zio.http.endpoint._

private[endpoint] final case class EndpointClient[I, E, O, M <: EndpointMiddleware](
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
          endpoint.error.decodeResponse(response).map(e => alt.left(e))

        val rightError = if (invocation.middleware == EndpointMiddleware.None) {
          ZIO.dieMessage("Middleware is none")
        } else {
          invocation.middleware.error
            .decodeResponse(response)
            .map(e => alt.right(e))
        }

        leftError.orElse(rightError).orDie.flip
      }
    }
  }
}
