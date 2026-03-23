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

package zio.http.metrics

import zio._
import zio.metrics._
import zio.metrics.connectors.prometheus.PrometheusPublisher
import zio.metrics.connectors.{MetricsConfig, prometheus}

import zio.http._

/**
 * One-stop shop for exposing Prometheus metrics over HTTP.
 *
 * Instruments every route with request/response metrics and appends a
 * `/metrics` endpoint that serves the Prometheus text format.
 *
 * {{{
 * Server
 *   .serve(MetricsEndpoint.withMetrics(myRoutes))
 *   .provide(Server.default, MetricsEndpoint.layers())
 * }}}
 */
object MetricsEndpoint {

  /**
   * Instruments routes with HTTP metrics and appends a Prometheus endpoint.
   *
   * This is the recommended one-call API. It applies `Middleware.metrics()` to
   * instrument your routes and appends a `GET /metrics` route that serves
   * Prometheus-formatted metrics.
   *
   * @param routes
   *   the application routes to instrument
   * @param metricsPath
   *   the path at which to expose Prometheus metrics (default: `"metrics"`)
   * @param concurrentRequestsName
   *   name of the gauge tracking concurrent requests
   * @param totalRequestsName
   *   name of the counter tracking total requests
   * @param requestDurationName
   *   name of the histogram tracking request durations
   * @param requestDurationBoundaries
   *   histogram bucket boundaries for request duration
   * @param extraLabels
   *   additional labels to attach to all metrics
   */
  def withMetrics[Env](
    routes: Routes[Env, Response],
    metricsPath: String = "metrics",
    concurrentRequestsName: String = "http_concurrent_requests_total",
    totalRequestsName: String = "http_requests_total",
    requestDurationName: String = "http_request_duration_seconds",
    requestDurationBoundaries: MetricKeyType.Histogram.Boundaries = Middleware.defaultBoundaries,
    extraLabels: Set[MetricLabel] = Set.empty,
  )(implicit trace: Trace): Routes[Env with PrometheusPublisher, Response] = {
    val instrumented = routes @@ Middleware.metrics(
      concurrentRequestsName = concurrentRequestsName,
      totalRequestsName = totalRequestsName,
      requestDurationName = requestDurationName,
      requestDurationBoundaries = requestDurationBoundaries,
      extraLabels = extraLabels,
    )
    instrumented ++ endpointRoute(metricsPath)
  }

  /**
   * A single route that serves Prometheus metrics. Use this if you want to
   * handle instrumentation separately (e.g. with `Middleware.metrics()`).
   */
  def endpointRoute(path: String = "metrics"): Routes[PrometheusPublisher, Response] =
    Routes(
      Method.GET / path -> handler(
        ZIO.serviceWithZIO[PrometheusPublisher](_.get.map(Response.text)),
      ),
    )

  /**
   * Combined ZIO layers for Prometheus metrics. Provides `PrometheusPublisher`.
   * Polls metrics at the specified interval (default: 1 second).
   */
  def layers(
    pollingInterval: Duration = 1.second,
  ): ZLayer[Any, Nothing, PrometheusPublisher] =
    ZLayer.succeed(MetricsConfig(pollingInterval)) ++
      prometheus.publisherLayer >+>
      prometheus.prometheusLayer
}
