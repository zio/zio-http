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
import zio.http.codec._
import zio.http.endpoint.internal.EndpointClient

/**
 * A [[zio.http.endpoint.EndpointExecutor]] is responsible for taking an
 * endpoint invocation, and executing the invocation, returning the final
 * result, or failing with a pre-defined RPC error.
 */
final case class EndpointExecutor[R, Auth, ReqEnv](
  client: ZClient[Any, ReqEnv, Body, Throwable, Response],
  locator: EndpointLocator,
  authProvider: ZIO[R, Nothing, Auth],
) {
  private val metadata = {
    implicit val trace0: Trace = Trace.empty
    zio.http.endpoint.internal
      .MemoizedZIO[Endpoint[_, _, _, _, _ <: AuthType], EndpointNotFound, EndpointClient[
        Any,
        Any,
        Any,
        Any,
        _,
      ]] { (api: Endpoint[_, _, _, _, _ <: AuthType]) =>
        locator.locate(api).map { location =>
          EndpointClient(
            location,
            api.asInstanceOf[Endpoint[Any, Any, Any, Any, _ <: AuthType]],
          )
        }
      }
  }

  private def getClient[P, I, E, O, A <: AuthType](
    endpoint: Endpoint[P, I, E, O, A],
  )(implicit trace: Trace): IO[EndpointNotFound, EndpointClient[P, I, E, O, A]] =
    metadata.get(endpoint).map(_.asInstanceOf[EndpointClient[P, I, E, O, A]])

  def apply[P, I, E, B, AuthT <: AuthType](
    invocation: Invocation[P, I, E, B, AuthT],
  )(implicit
    combiner: Combiner[I, invocation.endpoint.authType.ClientRequirement],
    ev: Auth <:< invocation.endpoint.authType.ClientRequirement,
    trace: Trace,
  ): ZIO[R & ReqEnv, E, B] = {
    getClient(invocation.endpoint).orDie.flatMap { endpointClient =>
      endpointClient.execute(
        client,
        invocation,
        authProvider.asInstanceOf[URIO[R, endpointClient.endpoint.authType.ClientRequirement]],
      )(
        combiner.asInstanceOf[Combiner[I, endpointClient.endpoint.authType.ClientRequirement]],
        trace,
      )
    }
  }

  def apply[P, I, E, B](
    invocation: Invocation[P, I, E, B, AuthType.None],
  )(implicit
    trace: Trace,
  ): ZIO[ReqEnv, E, B] = {
    getClient(invocation.endpoint).orDie.flatMap { endpointClient =>
      endpointClient.execute(client, invocation, ZIO.unit)(
        Combiner.rightUnit[I].asInstanceOf[Combiner[I, endpointClient.endpoint.authType.ClientRequirement]],
        trace,
      )
    }
  }
}
object EndpointExecutor {
  @deprecated("Use EndpointExecutor(client, url) instead.", since = "4.0.0")
  def apply[ReqEnv](
    client: ZClient[Any, ReqEnv, Body, Throwable, Response],
    locator: EndpointLocator,
  ): EndpointExecutor[Any, Unit, ReqEnv] =
    EndpointExecutor(client, locator, ZIO.unit)

  @deprecated("Use EndpointExecutor(client, url, auth) instead.", since = "4.0.0")
  def apply[Auth, ReqEnv](
    client: ZClient[Any, ReqEnv, Body, Throwable, Response],
    locator: EndpointLocator,
    auth: Auth,
  )(implicit
    trace: Trace,
  ): EndpointExecutor[Any, Auth, ReqEnv] =
    EndpointExecutor(client, locator, ZIO.succeed(auth))

  /** Preferred constructor: provide base URL directly. */
  def apply[ReqEnv](
    client: ZClient[Any, ReqEnv, Body, Throwable, Response],
    url: URL,
  ): EndpointExecutor[Any, Unit, ReqEnv] =
    EndpointExecutor(client, EndpointLocator.fromURL(url), ZIO.unit)

  /** Preferred constructor with explicit auth. */
  def apply[Auth, ReqEnv](
    client: ZClient[Any, ReqEnv, Body, Throwable, Response],
    url: URL,
    auth: Auth,
  )(implicit
    trace: Trace,
  ): EndpointExecutor[Any, Auth, ReqEnv] =
    EndpointExecutor(client, EndpointLocator.fromURL(url), ZIO.succeed(auth))

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

  def make[R: Tag, Auth: Tag](serviceName: String, authProvider: URIO[R, Auth])(implicit
    trace: Trace,
  ): ZLayer[Client, zio.Config.Error, EndpointExecutor[R, Auth, Scope]] =
    ZLayer {
      for {
        client <- ZIO.service[Client]
        config <- ZIO.config(Config.config.nested(serviceName))
      } yield EndpointExecutor(client, config.url, authProvider)
    }

  def make(
    serviceName: String,
  )(implicit trace: Trace): ZLayer[Client, zio.Config.Error, EndpointExecutor[Any, Unit, Scope]] =
    ZLayer {
      for {
        client <- ZIO.service[Client]
        config <- ZIO.config(Config.config.nested(serviceName))
      } yield EndpointExecutor(client, config.url)
    }
}
