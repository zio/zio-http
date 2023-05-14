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

        val rightError =
          invocation.middleware.error
            .decodeResponse(response)
            .map(e => alt.right(e))

        def generateErrorMessage(t1: Throwable, t2: Throwable) = {
          Cause.Both(
            Cause
              .fail(
                new IllegalStateException(s"Failed to deserialize response. Response status: ${response.status}"),
              ),
            Cause.Both(
              Cause
                .die(new IllegalStateException("Cannot deserialize using endpoint error codec", t1)),
              Cause
                .die(new IllegalStateException("Cannot deserialize using middleware error codec", t2)),
            ),
          )
        }

        def orElseCombineErrors[R, E1, A](
          zio1: ZIO[R, E1, A],
          zio2: ZIO[R, E1, A],
          f: (E1, E1) => Cause[E1],
        ): ZIO[R, E1, A] = {
          zio1.foldZIO(
            success = ZIO.succeed(_),
            failure = cause1 => {
              zio2.foldZIO(
                success = ZIO.succeed(_),
                failure = cause2 => {
                  ZIO.failCause(f(cause1, cause2))
                },
              )
            },
          )
        }

        orElseCombineErrors(leftError, rightError, generateErrorMessage).orDie.flip
      }
    }
  }
}
