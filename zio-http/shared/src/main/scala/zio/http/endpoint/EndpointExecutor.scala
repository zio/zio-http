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

import scala.annotation.nowarn

import zio._
import zio.stacktracer.TracingImplicits.disableAutoTrace

import zio.http._
import zio.http.codec._
import zio.http.endpoint.internal.EndpointClient

/**
 * Runs an endpoint [[Invocation]] as an HTTP call with `client` and returns the
 * decoded output, or fails with the endpoint's error type `E`. The `locator`
 * gives the base URL of each endpoint, and `authProvider` is run for each call
 * to produce the credentials the endpoint's auth type asks the client for.
 * Failures of the HTTP call itself, a response status that the endpoint does
 * not define, and an endpoint that the locator cannot locate end the effect
 * with a defect, not with `E`.
 *
 * The companion `EndpointExecutor.apply` overloads take a base `URL` and, when
 * the endpoints need credentials, an auth value or an effect that produces one.
 *
 * @see
 *   [[https://zio.dev/zio-http/reference/endpoint/ Endpoint]]
 */
@nowarn("msg=deprecated")
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

  /**
   * Executes `invocation`, adding the credentials that `authProvider` produces
   * to the request. Fails with the endpoint's error `E`.
   */
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

  /**
   * Executes an `invocation` of an endpoint without authentication. Fails with
   * the endpoint's error `E`.
   */
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

/**
 * Constructors for [[EndpointExecutor]]: from a client and a base URL, with an
 * optional auth value or auth effect, or as a layer read from configuration.
 */
object EndpointExecutor {

  /**
   * Returns an executor for `client` that finds each endpoint's URL with
   * `locator` and has no credentials.
   */
  @deprecated("Use apply overload with URL parameter", "3.6.0")
  def apply[ReqEnv](
    client: ZClient[Any, ReqEnv, Body, Throwable, Response],
    locator: EndpointLocator,
  ): EndpointExecutor[Any, Unit, ReqEnv] =
    EndpointExecutor(client, locator, ZIO.unit)

  /**
   * Returns an executor for `client` that finds each endpoint's URL with
   * `locator` and uses the fixed credentials `auth`.
   */
  @deprecated("Use apply overload with URL parameter", "3.6.0")
  def apply[Auth, ReqEnv](
    client: ZClient[Any, ReqEnv, Body, Throwable, Response],
    locator: EndpointLocator,
    auth: Auth,
  )(implicit
    trace: Trace,
  ): EndpointExecutor[Any, Auth, ReqEnv] =
    EndpointExecutor(client, locator, ZIO.succeed(auth))

  /**
   * Returns an executor for `client` that sends every endpoint to `location`.
   */
  @nowarn("msg=deprecated")
  def apply[ReqEnv](
    client: ZClient[Any, ReqEnv, Body, Throwable, Response],
    location: URL,
  )(implicit
    trace: Trace,
  ): EndpointExecutor[Any, Unit, ReqEnv] =
    EndpointExecutor(client, EndpointLocator.fromURL(location), ZIO.unit)

  /**
   * Returns an executor for `client` that sends every endpoint to `location`
   * and uses the fixed credentials `auth`.
   */
  @nowarn("msg=deprecated")
  def apply[Auth, ReqEnv](
    client: ZClient[Any, ReqEnv, Body, Throwable, Response],
    location: URL,
    auth: Auth,
  )(implicit
    trace: Trace,
  ): EndpointExecutor[Any, Auth, ReqEnv] =
    EndpointExecutor(client, EndpointLocator.fromURL(location), ZIO.succeed(auth))

  /**
   * Returns an executor for `client` that sends every endpoint to `location`
   * and runs `authProvider` for the credentials of each call.
   */
  @nowarn("msg=deprecated")
  def apply[R, Auth, ReqEnv](
    client: ZClient[Any, ReqEnv, Body, Throwable, Response],
    location: URL,
    authProvider: ZIO[R, Nothing, Auth],
  )(implicit
    trace: Trace,
  ): EndpointExecutor[R, Auth, ReqEnv] =
    EndpointExecutor(client, EndpointLocator.fromURL(location), authProvider)

  /** The configuration of an executor: the base `url` of the service. */
  final case class Config(url: URL)

  /** Holds the `ZConfig` description of the executor configuration. */
  object Config {
    import zio.{Config => ZConfig}

    /**
     * Reads a `Config` from the key `url`, which must hold a valid URI. Loading
     * fails with a `zio.Config.Error` when `url` is missing or is not a valid
     * URI.
     */
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

  /**
   * A layer that builds an executor from the `Client` in the environment and
   * the base URL at the key `url` nested under `serviceName` in the
   * configuration, using `authProvider` for credentials. Fails with a
   * `zio.Config.Error` when the configuration is missing or invalid.
   */
  @nowarn("msg=deprecated")
  def make[R: Tag, Auth: Tag](serviceName: String, authProvider: URIO[R, Auth])(implicit
    trace: Trace,
  ): ZLayer[Client, zio.Config.Error, EndpointExecutor[R, Auth, Scope]] =
    ZLayer {
      for {
        client <- ZIO.service[Client]
        config <- ZIO.config(Config.config.nested(serviceName))
      } yield EndpointExecutor(client, EndpointLocator.fromURL(config.url), authProvider)
    }

  /**
   * A layer that builds an executor without credentials from the `Client` in
   * the environment and the base URL at the key `url` nested under
   * `serviceName` in the configuration. Fails with a `zio.Config.Error` when
   * the configuration is missing or invalid.
   */
  @nowarn("msg=deprecated")
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
