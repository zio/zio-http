package zio.http.middleware

import zio._
import zio.http._
import zio.metrics.Metric.{Counter, Gauge, Histogram}
import zio.metrics.{Metric, MetricKeyType, MetricLabel}

private[zio] trait Metrics { self: RequestHandlerMiddlewares =>

  /**
   * Adds metrics to a zio-http server.
   *
   * @param pathLabelMapper
   *   A mapping function to map incoming paths to patterns, such as /users/1 to
   *   /users/:id.
   * @param totalRequestsName
   *   Total HTTP requests metric name.
   * @param requestDurationName
   *   HTTP request duration metric name.
   * @param requestDurationBoundaries
   *   Boundaries for the HTTP request duration metric.
   * @param extraLabels
   *   A set of extra labels all metrics will be tagged with.
   * @note
   *   When using Prometheus as your metrics backend, make sure to provide a
   *   `pathLabelMapper` in order to avoid
   *   [[https://prometheus.io/docs/practices/naming/#labels high cardinality labels]].
   */
  def metrics(
    pathLabelMapper: PartialFunction[Request, String] = Map.empty,
    concurrentRequestsName: String = "http_concurrent_requests_total",
    totalRequestsName: String = "http_requests_total",
    requestDurationName: String = "http_request_duration_seconds",
    requestDurationBoundaries: MetricKeyType.Histogram.Boundaries = Metrics.defaultBoundaries,
    extraLabels: Set[MetricLabel] = Set.empty,
  ): HttpRouteMiddleware[Any, Nothing] = {
    new HttpRouteMiddleware[Any, Nothing] {
      val requestsTotal: Counter[RuntimeFlags] = Metric.counterInt(totalRequestsName)
      val concurrentRequests: Gauge[Double]    = Metric.gauge(concurrentRequestsName)
      val requestDuration: Histogram[Double]   = Metric.histogram(requestDurationName, requestDurationBoundaries)
      val status404: Set[MetricLabel]          = Set(MetricLabel("status", "404"))
      val status500: Set[MetricLabel]          = Set(MetricLabel("status", "500"))
      val nanosToSeconds: Double               = 1e9d

      private def labelsForRequest(req: Request): Set[MetricLabel] =
        Set(
          MetricLabel("method", req.method.toString),
          MetricLabel("path", pathLabelMapper.lift(req).getOrElse(req.path.toString())),
        ) ++ extraLabels

      private def labelsForResponse(res: Response): Set[MetricLabel] =
        Set(
          MetricLabel("status", res.status.code.toString),
        )

      private def report(
        start: Long,
        requestLabels: Set[MetricLabel],
        labels: Set[MetricLabel],
      ): ZIO[Any, Nothing, Unit] =
        for {
          _   <- requestsTotal.tagged(labels).increment
          _   <- concurrentRequests.tagged(requestLabels).decrement
          end <- Clock.nanoTime
          took = end - start
          _ <- requestDuration.tagged(labels).update(took / nanosToSeconds)
        } yield ()

      override def apply[R1 <: Any, Err1 >: Nothing](
        route: HttpRoute[R1, Err1],
      )(implicit trace: Trace): HttpRoute[R1, Err1] =
        Route.fromHandlerZIO[Request] { req =>
          val requestLabels = labelsForRequest(req)

          for {
            start           <- Clock.nanoTime
            _               <- concurrentRequests.tagged(requestLabels).increment
            optionalHandler <- route.toHandler(req).toZIO.mapError(Some(_))
            handler         <-
              optionalHandler match {
                case Some(handler) =>
                  ZIO.succeed {
                    handler.onExit { exit =>
                      val labels =
                        requestLabels ++ exit.foldExit(
                          cause => cause.failureOption.fold(status500)(_ => status500),
                          labelsForResponse,
                        )

                      report(start, requestLabels, labels)
                    }
                  }
                case None          =>
                  report(start, requestLabels, requestLabels ++ status404) *>
                    ZIO.fail(None)
              }
          } yield handler
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
