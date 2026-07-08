/*
 * Copyright 2026 the ZIO HTTP contributors.
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
package zio.http.endpoint

import zio.blocks.endpoint.{AuthType, Endpoint}
import zio.http.{Halt, Handler, Request, Response, Route}
import zio.http.ResultType._

/**
  * Public server-side dispatch entry point emitted by `.implement`'s Scala 2
  * macro. It exists because a blackbox macro expands into the CALLER's scope
  * (possibly an external package), so the generated code may only reference
  * public members; this object provides the one public seam and delegates to the
  * `private[endpoint]` codec internals from inside this package.
  *
  * The user handler is already normalized by the macro into
  * `Input => Either[Err, Output]` (the internal union representation); users
  * never write `Left`/`Right` themselves.
  */
object EndpointServer {

  def implement[PathInput, Input, Err, Output, Auth <: AuthType](
    endpoint: Endpoint[PathInput, Input, Err, Output, Auth],
    handler: Input => Either[Err, Output],
  ): Route[Any] = {
    val handlerFn: Request => (Response | Halt) = { request =>
      EndpointCodec.decodeRequest(endpoint.input, request) match {
        case Right(input) =>
          handler(input) match {
            case Left(err)  => EndpointCodec.encodeResponse(endpoint.error, err, 400)
            case Right(out) => EndpointCodec.encodeResponse(endpoint.output, out, 200)
          }
        case Left(_) =>
          Response.badRequest
      }
    }
    Route(endpoint.route, Handler(handlerFn))
  }
}
