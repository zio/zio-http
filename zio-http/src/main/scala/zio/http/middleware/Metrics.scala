package zio.http.middleware

import zio.http.{Http, Middleware, Request, Response}
import zio._
import zio.metrics.{Metric, MetricKeyType, MetricLabel}

private[zio] trait Metrics {
  def metrics(
    pathLabelMapper: PartialFunction[Request, String] = Map.empty,
    concurrentRequestsName: String = "http_concurrent_requests_total",
    totalRequestsName: String = "http_requests_total",
    requestDurationName: String = "http_request_duration_seconds",
    requestDurationBoundaries: MetricKeyType.Histogram.Boundaries = Metrics.defaultBoundaries,
  ): HttpMiddleware[Any, Nothing] = {
    new Middleware[Any, Nothing, Request, Response, Request, Response] {
      val requestsTotal      = Metric.counterInt(totalRequestsName)
      val concurrentRequests = Metric.gauge(concurrentRequestsName)
      val requestDuration    = Metric.histogram(requestDurationName, requestDurationBoundaries)
      val status404          = Set(MetricLabel("status", "404"))
      val status500          = Set(MetricLabel("status", "500"))
      val nanosToSeconds     = 1e9d

      def labelsForRequest(req: Request): Set[MetricLabel] =
        Set(
          MetricLabel("method", req.method.toString),
          MetricLabel("path", pathLabelMapper.lift(req).getOrElse(req.path.toString())),
        )

      def labelsForResponse(res: Response): Set[MetricLabel] =
        Set(
          MetricLabel("status", res.status.code.toString),
        )

      def apply[R1 <: Any, E1 >: Nothing](
        http: Http[R1, E1, Request, Response],
      ): Http[R1, E1, Request, Response] =
        Http.fromOptionFunction[Request] { req =>
          val requestLabels = labelsForRequest(req)

          for {
            start <- Clock.nanoTime
            _     <- concurrentRequests.tagged(requestLabels).increment
            res   <- http(req).onExit(exit => {
              val labels =
                requestLabels ++ exit.foldExit(
                  cause => cause.failureOption.fold(status500)(failure => failure.fold(status404)(_ => status500)),
                  labelsForResponse,
                )

              for {
                _   <- requestsTotal.tagged(labels).increment
                _   <- concurrentRequests.tagged(requestLabels).decrement
                end <- Clock.nanoTime
                took = end - start
                _ <- requestDuration.tagged(labels).update(took / nanosToSeconds)
              } yield ()
            })
          } yield res
        }
    }
  }
}

object Metrics {
  // Prometheus defaults
  private[zio] val defaultBoundaries = MetricKeyType.Histogram.Boundaries.fromChunk(
    Chunk(
      .005, .01, .025, .05, .075, .1, .25, .5, .75, 1, 2.5, 5, 7.5, 10,
    ),
  )
}
