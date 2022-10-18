package zio.http.middleware

import zio.http.Middleware.metrics
import zio.http._
import zio.http.internal.HttpAppTestExtensions
import zio._
import zio.metrics.{Metric, MetricState}
import zio.test._
import zio.metrics.MetricLabel

object MetricsSpec extends ZIOSpecDefault with HttpAppTestExtensions {
  override def spec = suite("MetricsSpec")(
    test("http_requests_total & http_errors_total") {
      val app = Http
        .collect[Request] {
          case Method.GET -> !! / "ok"     => Http.ok
          case Method.GET -> !! / "error"  => Http.error(HttpError.InternalServerError())
          case Method.GET -> !! / "defect" => Http.die(new Throwable("boom"))
        }
        .flatten @@ metrics(extraLabels = Set(MetricLabel("test", "http_requests_total & http_errors_total")))

      val total   = Metric.counterInt("http_requests_total").tagged("test", "http_requests_total & http_errors_total")
      val totalOk = total.tagged("path", "/ok").tagged("method", "GET").tagged("status", "200")
      val totalErrors   = total.tagged("path", "/error").tagged("method", "GET").tagged("status", "500")
      val totalDefects  = total.tagged("path", "/defect").tagged("method", "GET").tagged("status", "500")
      val totalNotFound = total.tagged("path", "/not-found").tagged("method", "GET").tagged("status", "404")

      for {
        _ <- app(Request(method = Method.GET, url = URL(!! / "ok")))
        _ <- app(Request(method = Method.GET, url = URL(!! / "error")))
        _ <- app(Request(method = Method.GET, url = URL(!! / "defect"))).catchAllDefect(_ => ZIO.unit)
        _ <- app(Request(method = Method.GET, url = URL(!! / "not-found"))).ignore.catchAllDefect(_ => ZIO.unit)
        totalOkCount       <- totalOk.value
        totalErrorsCount   <- totalErrors.value
        totalDefectsCount  <- totalDefects.value
        totalNotFoundCount <- totalNotFound.value
      } yield assertTrue(totalOkCount == MetricState.Counter(1)) &&
        assertTrue(totalErrorsCount == MetricState.Counter(1)) &&
        assertTrue(totalDefectsCount == MetricState.Counter(1)) &&
        assertTrue(totalNotFoundCount == MetricState.Counter(1))
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

      val app = Http
        .collect[Request] { case Method.GET -> !! / "ok" => Http.ok }
        .flatten @@ metrics(extraLabels = Set(MetricLabel("test", "http_request_duration_seconds")))

      for {
        _        <- app(Request(method = Method.GET, url = URL(!! / "ok")))
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
          .collect[Request] { case Method.GET -> !! / "slow" =>
            Http.fromZIO(promise.succeed(())) *> Http.ok.delay(10.seconds)
          }
          .flatten @@ metrics(extraLabels = Set(MetricLabel("test", "http_concurrent_requests_total")))
        before <- gauge.value
        fiber  <- app(Request(method = Method.GET, url = URL(!! / "slow"))).fork
        _      <- promise.await
        during <- gauge.value
        _      <- TestClock.adjust(11.seconds)
        after  <- gauge.value
      } yield assertTrue(before == MetricState.Gauge(0)) &&
        assertTrue(before == after) && assertTrue(during == MetricState.Gauge(1))
    },
  )
}
