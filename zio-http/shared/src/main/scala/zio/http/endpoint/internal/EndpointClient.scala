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
import zio.http.endpoint.internal.EndpointClient.protobufMediaType

private[endpoint] final case class EndpointClient[P, I, E, O, A <: AuthType](
  endpointRoot: URL,
  endpoint: Endpoint[P, I, E, O, A],
) {
  def execute[R, ReqEnv](
    client: ZClient[Any, ReqEnv, Body, Throwable, Response],
    invocation: Invocation[P, I, E, O, A],
    authProvider: URIO[R, endpoint.authType.ClientRequirement],
  )(implicit
    combiner: Combiner[I, endpoint.authType.ClientRequirement],
    trace: Trace,
  ): ZIO[R & ReqEnv, E, O] = {
    def request0(config: CodecConfig, authInput: endpoint.authType.ClientRequirement) = {
      val input = if (authInput.isInstanceOf[Unit]) invocation.input else combiner.combine(invocation.input, authInput)
      endpoint
        .authedInput(combiner)
        .asInstanceOf[HttpCodec[HttpCodecType.RequestType, Any]]
        .encodeRequest(input, config)
    }
    def request(config: CodecConfig, authInput: endpoint.authType.ClientRequirement)  = {
      val req0 = request0(config, authInput)
      req0.copy(url = endpointRoot ++ req0.url)
    }

    def withDefaultAcceptHeader(config: CodecConfig, authInput: endpoint.authType.ClientRequirement) = {
      val req = request(config, authInput)
      if (req.headers.exists(_.headerName == Header.Accept.name))
        req
      else {
        req.addHeader(
          Header.Accept(MediaType.application.json, protobufMediaType, MediaType.text.`plain`),
        )
      }
    }

    val requested =
      for {
        authInput <- authProvider
        config    <- CodecConfig.codecRef.get
        response  <- client.request(withDefaultAcceptHeader(config, authInput)).orDie
      } yield response

    requested.flatMap { response =>
      if (endpoint.output.matchesStatus(response.status)) {
        endpoint.output.decodeResponse(response).orDie
      } else if (endpoint.error.matchesStatus(response.status)) {
        endpoint.error.decodeResponse(response).orDie.flip
      } else {
        val error = endpoint.codecError.decodeResponse(response)
        error
          .flatMap(codecError => ZIO.die(codecError))
          .orElse(ZIO.die(new IllegalStateException(s"Status code: ${response.status} is not defined in the endpoint")))
      }
    }
  }
}

object EndpointClient {
  private[internal] val protobufMediaType: MediaType = MediaType.parseCustomMediaType("application/protobuf").get
}
