package zio.http.middleware

import zio.http.{Http, Middleware, Request, Response}
import zio._
import zio.metrics.{Metric, MetricKeyType, MetricLabel}

private[zio] trait Metrics {
  def metrics[R, E](
    mapper: Metrics.Mapper = Map.empty,
    boundaries: MetricKeyType.Histogram.Boundaries = Metrics.defaultBoundaries,
  ): HttpMiddleware[R with Metrics.InFlightRequests, E] =
    new Middleware[R with Metrics.InFlightRequests, E, Request, Response, Request, Response] {
      private def metricLabelsForRequest(req: Request): Set[MetricLabel] =
        Set(
          MetricLabel("method", req.method.toString),
          MetricLabel("path", mapper.lift(req).getOrElse(req.path.toString())),
        )

      private def withRequestMeta[Type, In, Out](metric: Metric[Type, In, Out])(req: Request) =
        metric.tagged(metricLabelsForRequest(req))

      private def requestsTotal(req: Request) =
        withRequestMeta(Metric.counterInt("http_requests_total"))(req)

      private val errorCount = Metric.counterInt("http_errors_total")

      private def errorCountWithRequest(req: Request) =
        withRequestMeta(errorCount)(req)

      private def errorCountWithResponse(req: Request, res: Response) =
        errorCountWithStatus(req, res.status.code.toString)

      private def errorCountWithStatus(req: Request, status: String) =
        errorCountWithRequest(req).tagged("status_code", status)

      private def requestDuration(req: Request) =
        withRequestMeta(
          Metric.histogram(
            "http_request_duration_seconds",
            boundaries,
          ),
        )(req).trackDurationWith(toSeconds)

      private val NANOS                  = 1e9d
      private def toSeconds(d: Duration) =
        (d.getSeconds() * NANOS + d.getNano()) / NANOS

      def apply[R1 <: R with Metrics.InFlightRequests, E1 >: E](
        http: Http[R1, E1, Request, Response],
      ): Http[R1, E1, Request, Response] =
        Http.fromOptionFunction[Request] { req =>
          val labels = metricLabelsForRequest(req)
          Metrics.InFlightRequests.increment(labels) *>
            (http(req)
              .ensuring(requestsTotal(req).increment *> Metrics.InFlightRequests.decrement(labels))
              .tapBoth(
                err =>
                  err.fold(errorCountWithStatus(req, "404").increment)(_ => errorCountWithStatus(req, "500").increment),
                res => ZIO.when(res.status.code > 399)(errorCountWithResponse(req, res).increment),
              )
              .tapDefect(_ => errorCountWithStatus(req, "500").increment)) @@ requestDuration(req)
        }
    }
}

object Metrics {
  type Mapper = PartialFunction[Request, String]

  // Prometheus defaults
  private[zio] val defaultBoundaries = MetricKeyType.Histogram.Boundaries.fromChunk(
    Chunk(
      .005, .01, .025, .05, .075, .1, .25, .5, .75, 1, 2.5, 5, 7.5, 10,
    ),
  )

  trait InFlightRequests {
    def increment(labels: Set[MetricLabel]): UIO[Int]
    def decrement(labels: Set[MetricLabel]): UIO[Int]
    def get: UIO[Int]
  }

  object InFlightRequests {
    def increment(labels: Set[MetricLabel]): URIO[InFlightRequests, Any] = ZIO.serviceWithZIO(_.increment(labels))
    def decrement(labels: Set[MetricLabel]): URIO[InFlightRequests, Any] = ZIO.serviceWithZIO(_.decrement(labels))
    val get: URIO[InFlightRequests, Int]                                 = ZIO.serviceWithZIO(_.get)

    val live = ZLayer.fromZIO {
      for {
        ref <- Ref.make(0)
        gauge = Metric.gauge("http_inflight_requests_total").contramap[Int](_.toDouble)
      } yield new InFlightRequests {
        def increment(labels: Set[MetricLabel]): UIO[Int] = ref.updateAndGet(_ + 1) @@ gauge.tagged(labels.toSet)

        def decrement(labels: Set[MetricLabel]): UIO[Int] = ref.updateAndGet(_ - 1) @@ gauge.tagged(labels.toSet)

        def get: UIO[Int] = ref.get
      }
    }
  }

  val live = Metrics.InFlightRequests.live
}
