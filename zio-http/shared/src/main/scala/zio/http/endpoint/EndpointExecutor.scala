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

package zio.http.endpoint

import zio._
import zio.stacktracer.TracingImplicits.disableAutoTrace

import zio.http._
import zio.http.codec.{Alternator, Combiner}
import zio.http.endpoint.internal.EndpointClient

/**
 * A [[zio.http.endpoint.EndpointExecutor]] is responsible for taking an
 * endpoint invocation, and executing the invocation, returning the final
 * result, or failing with a pre-defined RPC error.
 */
final case class EndpointExecutor(
  client: Client,
  locator: EndpointLocator,
) {
  private val metadata = {
    implicit val trace0 = Trace.empty
    zio.http.endpoint.internal
      .MemoizedZIO[Endpoint[_, _, _, _, _ <: AuthType], EndpointNotFound, EndpointClient[
        Any,
        Any,
        Any,
        Any,
        _,
        Any,
      ]] { (api: Endpoint[_, _, _, _, _ <: AuthType]) =>
        locator.locate(api).map { location =>
          EndpointClient(
            location,
            api.asInstanceOf[Endpoint.WithAuthInput[Any, Any, Any, Any, _ <: AuthType, Any]],
          )
        }
      }
  }

  private def getClient[P, I, E, O, A <: AuthType, AI](
    endpoint: Endpoint.WithAuthInput[P, I, E, O, A, AI],
  )(implicit trace: Trace): IO[EndpointNotFound, EndpointClient[P, I, E, O, A, AI]] =
    metadata.get(endpoint).map(_.asInstanceOf[EndpointClient[P, I, E, O, A, AI]])

  def apply[P, A, E, B, Auth <: AuthType, AI](
    invocation: Invocation[P, A, E, B, Auth, AI],
  )(implicit
    combiner: Combiner[A, invocation.endpoint.authType.ClientRequirement],
    trace: Trace,
  ): ZIO[Scope, E, B] = {
    getClient(invocation.endpoint).orDie.flatMap { endpointClient =>
      endpointClient.execute(client, invocation)(
        combiner.asInstanceOf[Combiner[A, endpointClient.endpoint.authType.ClientRequirement]],
        trace,
      )
    }
  }
}
object EndpointExecutor {
  final case class Config(url: URL)
  object Config {
    import zio.{Config => ZConfig}
    val config: ZConfig[Config] =
      ZConfig
        .uri("url")
        .map { uri =>
          URL
            .decode(uri.toString)
            .getOrElse(throw new RuntimeException(s"Illegal format of URI ${uri} for endpoint executor configuration"))
        }
        .map(Config(_))
  }

  def make(serviceName: String)(implicit trace: Trace): ZLayer[Client, zio.Config.Error, EndpointExecutor] =
    ZLayer {
      for {
        client <- ZIO.service[Client]
        config <- ZIO.config(Config.config.nested(serviceName))
      } yield EndpointExecutor(client, EndpointLocator.fromURL(config.url))
    }
}
