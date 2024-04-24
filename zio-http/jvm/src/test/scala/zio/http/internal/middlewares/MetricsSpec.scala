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

import zio.http.Middleware.metrics
import zio.http._
import zio.http.codec.{PathCodec, SegmentCodec}
import zio.http.internal.HttpAppTestExtensions

object MetricsSpec extends ZIOHttpSpec with HttpAppTestExtensions {
  override def spec: Spec[TestEnvironment with Scope, Any] =
    suite("MetricsSpec")(
      test("http_requests_total & http_errors_total") {
        val app = Routes(
          Method.GET / "ok"     -> Handler.ok,
          Method.GET / "error"  -> Handler.internalServerError,
          Method.GET / "fail"   -> Handler.fail(Response.status(Status.Forbidden)),
          Method.GET / "defect" -> Handler.die(new Throwable("boom")),
        ) @@ metrics(
          extraLabels = Set(MetricLabel("test", "http_requests_total & http_errors_total")),
        )

        val total   = Metric.counterInt("http_requests_total").tagged("test", "http_requests_total & http_errors_total")
        val totalOk = total.tagged("path", "/ok").tagged("method", "GET").tagged("status", "200")
        val totalErrors  = total.tagged("path", "/error").tagged("method", "GET").tagged("status", "500")
        val totalFails   = total.tagged("path", "/fail").tagged("method", "GET").tagged("status", "403")
        val totalDefects = total.tagged("path", "/defect").tagged("method", "GET").tagged("status", "500")

        for {
          _                 <- app.runZIO(Request.get("/ok"))
          _                 <- app.runZIO(Request.get("/error"))
          _                 <- app.runZIO(Request.get("/fail")).exit
          _                 <- app.runZIO(Request.get("/defect")).exit
          totalOkCount      <- totalOk.value
          totalErrorsCount  <- totalErrors.value
          totalFailsCount   <- totalFails.value
          totalDefectsCount <- totalDefects.value
        } yield assertTrue(
          totalOkCount == MetricState.Counter(1),
          totalErrorsCount == MetricState.Counter(1),
          totalFailsCount == MetricState.Counter(1),
          totalDefectsCount == MetricState.Counter(1),
        )
      },
      test("http_requests_total with path label mapper") {
        val routes = (Method.GET / "user" / int("id") -> Handler.ok).toRoutes @@ metrics(
          extraLabels = Set(MetricLabel("test", "http_requests_total with path label mapper")),
        )

        val total   =
          Metric.counterInt("http_requests_total").tagged("test", "http_requests_total with path label mapper")
        val totalOk = total.tagged("path", "/user/{id}").tagged("method", "GET").tagged("status", "200")

        for {
          _            <- routes.runZIO(Request.get(url = URL(Path.root / "user" / "1")))
          _            <- routes.runZIO(Request.get(url = URL(Path.root / "user" / "2")))
          totalOkCount <- totalOk.value
        } yield assertTrue(totalOkCount == MetricState.Counter(2))
      },
      test("http_request_duration_seconds") {
        val histogram = Metric
          .histogram(
            "http_request_duration_seconds",
            Middleware.defaultBoundaries,
          )
          .tagged("test", "http_request_duration_seconds")
          .tagged("path", "/ok")
          .tagged("method", "GET")
          .tagged("status", "200")

        val app: Routes[Any, Response] =
          (Method.GET / "ok" -> Handler.ok).toRoutes @@ metrics(extraLabels =
            Set(MetricLabel("test", "http_request_duration_seconds")),
          )

        for {
          _        <- app.runZIO(Request.get(url = URL(Path.root / "ok")))
          observed <- histogram.value.map(_.buckets.exists { case (_, count) => count > 0 })
        } yield assertTrue(observed)
      },
      test("http_concurrent_requests_total") {
        val gauge = Metric
          .gauge("http_concurrent_requests_total")
          .tagged("test", "http_concurrent_requests_total")
          .tagged("path", "/...")
          .tagged("method", "*")

        for {
          promise <- Promise.make[Nothing, Unit]
          app = Routes(
            Method.ANY / PathCodec.trailing -> (Handler.fromZIO(promise.succeed(())) *> Handler.ok.delay(10.seconds)),
          ) @@ metrics(extraLabels = Set(MetricLabel("test", "http_concurrent_requests_total")))
          before <- gauge.value
          _      <- app.runZIO(Request.get(url = URL(Path.root / "slow"))).fork
          _      <- promise.await
          during <- gauge.value
          _      <- TestClock.adjust(11.seconds)
          after  <- gauge.value
        } yield assertTrue(before == MetricState.Gauge(0), before == after, during == MetricState.Gauge(1))
      },
    )
}
