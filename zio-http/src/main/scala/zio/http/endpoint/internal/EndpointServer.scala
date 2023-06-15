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

import zio.http.Header.Accept.MediaTypeWithQFactor
import zio.http._
import zio.http.endpoint.internal.EndpointServer.defaultMediaTypes
import zio.http.endpoint.{EndpointMiddleware, Routes}

private[endpoint] final case class EndpointServer[R, E, I, O, M <: EndpointMiddleware](
  single: Routes.Single[R, E, I, O, M],
) {
  private val endpoint = single.endpoint
  private val handler  = single.handler

  def handle(request: Request)(implicit trace: Trace): ZIO[R, Nothing, Response] = {
    val outputMediaTypes = request.headers
      .get(Header.Accept)
      .map(_.mimeTypes)
      .getOrElse(defaultMediaTypes)
    endpoint.input.decodeRequest(request).orDie.flatMap { value =>
      handler(value).map(endpoint.output.encodeResponse(_, outputMediaTypes)).catchAll { error =>
        ZIO.succeed(single.endpoint.error.encodeResponse(error, outputMediaTypes))
      }
    }
  }
}

object EndpointServer {
  private[internal] val defaultMediaTypes =
    NonEmptyChunk(MediaTypeWithQFactor(MediaType.application.`json`, Some(1)))
}
