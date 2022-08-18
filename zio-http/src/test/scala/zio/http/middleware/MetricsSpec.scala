package zhttp.http.middleware

import zhttp.http.Middleware.metrics
import zhttp.http._
import zhttp.internal.HttpAppTestExtensions
import zio._
import zio.metrics.{Metric, MetricState}
import zio.test.TestAspect.{sequential, withLiveClock}
import zio.test._

object MetricsSpec extends ZIOSpecDefault with HttpAppTestExtensions {
  override def spec = suite("MetricsSpec")(
    test("http_requests_total & http_errors_total") {
      val app = Http
        .collect[Request] {
          case Method.GET -> !! / "ok"     => Http.ok
          case Method.GET -> !! / "error"  => Http.error(HttpError.InternalServerError())
          case Method.GET -> !! / "defect" => Http.die(new Throwable("boom"))
        }
        .flatten @@ metrics()

      val total        = Metric.counterInt("http_requests_total")
      val totalOk      = total.tagged("path", "/ok").tagged("method", "GET")
      val totalErrors  = total.tagged("path", "/error").tagged("method", "GET")
      val totalDefects = total.tagged("path", "/defect").tagged("method", "GET")

      val errors         = Metric.counterInt("http_errors_total")
      val errorsErrors   = errors.tagged("path", "/error").tagged("method", "GET").tagged("status_code", "500")
      val errorsDefects  = errors.tagged("path", "/defect").tagged("method", "GET").tagged("status_code", "500")
      val errorsNotFound = errors.tagged("path", "/not-found").tagged("method", "GET").tagged("status_code", "404")

      for {
        _ <- app(Request(method = Method.GET, url = URL(!! / "ok")))
        _ <- app(Request(method = Method.GET, url = URL(!! / "error")))
        _ <- app(Request(method = Method.GET, url = URL(!! / "defect"))).catchAllDefect(_ => ZIO.unit)
        _ <- app(Request(method = Method.GET, url = URL(!! / "not-found"))).ignore.catchAllDefect(_ => ZIO.unit)
        totalOkCount        <- totalOk.value
        totalErrorsCount    <- totalErrors.value
        totalDefectsCount   <- totalDefects.value
        errorsErrorsCount   <- errorsErrors.value
        errorsDefectsCount  <- errorsDefects.value
        errorsNotFoundCount <- errorsNotFound.value
      } yield assertTrue(totalOkCount == MetricState.Counter(1)) &&
        assertTrue(totalErrorsCount == MetricState.Counter(1)) &&
        assertTrue(totalDefectsCount == MetricState.Counter(1)) &&
        assertTrue(errorsErrorsCount == MetricState.Counter(1)) &&
        assertTrue(errorsDefectsCount == MetricState.Counter(1)) &&
        assertTrue(errorsNotFoundCount == MetricState.Counter(1))
    },
    test("http_request_duration_seconds") {
      val histogram = Metric
        .histogram(
          "http_request_duration_seconds",
          Metrics.defaultBoundaries,
        )
        .tagged("path", "/ok")
        .tagged("method", "GET")

      for {
        observed <- histogram.value.map(_.buckets.exists { case (_, count) => count > 0 })
      } yield assertTrue(observed)
    },
    test("http_inflight_requests_total") {
      val gauge = Metric.gauge("http_inflight_requests_total").tagged("path", "/slow").tagged("method", "GET")

      for {
        promise <- Promise.make[Nothing, Unit]
        app = Http
          .collect[Request] { case Method.GET -> !! / "slow" =>
            Http.fromZIO(promise.succeed(())) *> Http.ok.delay(10.seconds)
          }
          .flatten @@ metrics()
        before <- gauge.value
        fiber  <- app(Request(method = Method.GET, url = URL(!! / "slow"))).fork
        _      <- promise.await
        during <- gauge.value
        _      <- fiber.interrupt
        after  <- gauge.value
      } yield assertTrue(before == MetricState.Gauge(0)) &&
        assertTrue(before == after) && assertTrue(during == MetricState.Gauge(1))
    } @@ withLiveClock,
  ).provideSomeLayerShared[TestEnvironment](Metrics.live) @@ sequential
}
