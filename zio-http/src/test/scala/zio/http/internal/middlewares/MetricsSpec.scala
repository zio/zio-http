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

package zio.http.internal.middlewares

import zio._
import zio.metrics.{Metric, MetricLabel, MetricState}
import zio.test._

import zio.http.HttpAppMiddleware.metrics
import zio.http._
import zio.http.internal.HttpAppTestExtensions

object MetricsSpec extends ZIOSpecDefault with HttpAppTestExtensions {
  override def spec: Spec[TestEnvironment with Scope, Any] =
    suite("MetricsSpec")(
      test("http_requests_total & http_errors_total") {
        val app = Http
          .collectHandler[Request] {
            case Method.GET -> Root / "ok"        => Handler.ok
            case Method.GET -> Root / "error"     => Handler.error(HttpError.InternalServerError())
            case Method.GET -> Root / "fail"      => Handler.fail(Response.status(Status.Forbidden))
            case Method.GET -> Root / "defect"    => Handler.die(new Throwable("boom"))
            case Method.GET -> Root / "not-found" => Handler.notFound
          } @@ metrics(
          extraLabels = Set(MetricLabel("test", "http_requests_total & http_errors_total")),
        )

        val total   = Metric.counterInt("http_requests_total").tagged("test", "http_requests_total & http_errors_total")
        val totalOk = total.tagged("path", "/ok").tagged("method", "GET").tagged("status", "200")
        val totalErrors   = total.tagged("path", "/error").tagged("method", "GET").tagged("status", "500")
        val totalFails    = total.tagged("path", "/fail").tagged("method", "GET").tagged("status", "403")
        val totalDefects  = total.tagged("path", "/defect").tagged("method", "GET").tagged("status", "500")
        val totalNotFound = total.tagged("path", "/not-found").tagged("method", "GET").tagged("status", "404")

        for {
          _            <- app.runZIO(Request.get(url = URL(Root / "ok")))
          _            <- app.runZIO(Request.get(url = URL(Root / "error")))
          _            <- app.runZIO(Request.get(url = URL(Root / "fail"))).ignore
          _            <- app.runZIO(Request.get(url = URL(Root / "defect"))).catchAllDefect(_ => ZIO.unit)
          _            <- app.runZIO(Request.get(url = URL(Root / "not-found"))).ignore.catchAllDefect(_ => ZIO.unit)
          totalOkCount <- totalOk.value
          totalErrorsCount   <- totalErrors.value
          totalFailsCount    <- totalFails.value
          totalDefectsCount  <- totalDefects.value
          totalNotFoundCount <- totalNotFound.value
        } yield assertTrue(
          totalOkCount == MetricState.Counter(1),
          totalErrorsCount == MetricState.Counter(1),
          totalFailsCount == MetricState.Counter(1),
          totalDefectsCount == MetricState.Counter(1),
          totalNotFoundCount == MetricState.Counter(1),
        )
      },
      test("http_requests_total with path label mapper") {
        val app = Handler.ok.toHttp @@ metrics(
          pathLabelMapper = { case Method.GET -> Root / "user" / _ =>
            "/user/:id"
          },
          extraLabels = Set(MetricLabel("test", "http_requests_total with path label mapper")),
        )

        val total   =
          Metric.counterInt("http_requests_total").tagged("test", "http_requests_total with path label mapper")
        val totalOk = total.tagged("path", "/user/:id").tagged("method", "GET").tagged("status", "200")

        for {
          _            <- app.runZIO(Request.get(url = URL(Root / "user" / "1")))
          _            <- app.runZIO(Request.get(url = URL(Root / "user" / "2")))
          totalOkCount <- totalOk.value
        } yield assertTrue(totalOkCount == MetricState.Counter(2))
      },
      test("http_request_duration_seconds") {
        val histogram = Metric
          .histogram(
            "http_request_duration_seconds",
            Metrics.defaultBoundaries,
          )
          .tagged("test", "http_request_duration_seconds")
          .tagged("path", "/ok")
          .tagged("method", "GET")
          .tagged("status", "200")

        val app: HttpApp[Any, Nothing] =
          Handler.ok.toHttp @@ metrics(extraLabels = Set(MetricLabel("test", "http_request_duration_seconds")))

        for {
          _        <- app.runZIO(Request.get(url = URL(Root / "ok")))
          observed <- histogram.value.map(_.buckets.exists { case (_, count) => count > 0 })
        } yield assertTrue(observed)
      },
      test("http_concurrent_requests_total") {
        val gauge = Metric
          .gauge("http_concurrent_requests_total")
          .tagged("test", "http_concurrent_requests_total")
          .tagged("path", "/slow")
          .tagged("method", "GET")

        for {
          promise <- Promise.make[Nothing, Unit]
          app = Http
            .collectHandler[Request] { case _ =>
              Handler.fromZIO(promise.succeed(())) *> Handler.ok.delay(10.seconds)
            } @@ metrics(extraLabels = Set(MetricLabel("test", "http_concurrent_requests_total")))
          before <- gauge.value
          _      <- app.runZIO(Request.get(url = URL(Root / "slow"))).fork
          _      <- promise.await
          during <- gauge.value
          _      <- TestClock.adjust(11.seconds)
          after  <- gauge.value
        } yield assertTrue(before == MetricState.Gauge(0), before == after, during == MetricState.Gauge(1))
      },
    )
}
