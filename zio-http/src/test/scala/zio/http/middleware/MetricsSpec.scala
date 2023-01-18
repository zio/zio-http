package zio.http.middleware

import zio._
import zio.http.Middleware.metrics
import zio.http._
import zio.http.internal.HttpAppTestExtensions
import zio.http.model._
import zio.metrics.{Metric, MetricLabel, MetricState}
import zio.test._

object MetricsSpec extends ZIOSpecDefault with HttpAppTestExtensions {
  override def spec = suite("MetricsSpec")(
    test("http_requests_total & http_errors_total") {
      val app = Http
        .collectHandler[Request] {
          case Method.GET -> !! / "ok"     => Handler.ok
          case Method.GET -> !! / "error"  => Handler.error(HttpError.InternalServerError())
          case Method.GET -> !! / "defect" => Handler.die(new Throwable("boom"))
        } @@ metrics(
        extraLabels = Set(MetricLabel("test", "http_requests_total & http_errors_total")),
      )

      val total   = Metric.counterInt("http_requests_total").tagged("test", "http_requests_total & http_errors_total")
      val totalOk = total.tagged("path", "/ok").tagged("method", "GET").tagged("status", "200")
      val totalErrors   = total.tagged("path", "/error").tagged("method", "GET").tagged("status", "500")
      val totalDefects  = total.tagged("path", "/defect").tagged("method", "GET").tagged("status", "500")
      val totalNotFound = total.tagged("path", "/not-found").tagged("method", "GET").tagged("status", "404")

      for {
        _                  <- app.runZIO(Request.get(url = URL(!! / "ok")))
        _                  <- app.runZIO(Request.get(url = URL(!! / "error")))
        _                  <- app.runZIO(Request.get(url = URL(!! / "defect"))).catchAllDefect(_ => ZIO.unit)
        _                  <- app.runZIO(Request.get(url = URL(!! / "not-found"))).ignore.catchAllDefect(_ => ZIO.unit)
        totalOkCount       <- totalOk.value
        totalErrorsCount   <- totalErrors.value
        totalDefectsCount  <- totalDefects.value
        totalNotFoundCount <- totalNotFound.value
      } yield assertTrue(totalOkCount == MetricState.Counter(1)) &&
        assertTrue(totalErrorsCount == MetricState.Counter(1)) &&
        assertTrue(totalDefectsCount == MetricState.Counter(1)) &&
        assertTrue(totalNotFoundCount == MetricState.Counter(1))
    },
    test("http_requests_total with path label mapper") {
      val app = Handler.ok.toHttp @@ metrics(
        pathLabelMapper = { case Method.GET -> !! / "user" / _ =>
          "/user/:id"
        },
        extraLabels = Set(MetricLabel("test", "http_requests_total with path label mapper")),
      )

      val total = Metric.counterInt("http_requests_total").tagged("test", "http_requests_total with path label mapper")
      val totalOk = total.tagged("path", "/user/:id").tagged("method", "GET").tagged("status", "200")

      for {
        _            <- app.runZIO(Request.get(url = URL(!! / "user" / "1")))
        _            <- app.runZIO(Request.get(url = URL(!! / "user" / "2")))
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
        _        <- app.runZIO(Request.get(url = URL(!! / "ok")))
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
        fiber  <- app.runZIO(Request.get(url = URL(!! / "slow"))).fork
        _      <- promise.await
        during <- gauge.value
        _      <- TestClock.adjust(11.seconds)
        after  <- gauge.value
      } yield assertTrue(before == MetricState.Gauge(0)) &&
        assertTrue(before == after) && assertTrue(during == MetricState.Gauge(1))
    },
  )
}
