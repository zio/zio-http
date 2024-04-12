package example

import zio._
import zio.http._
import zio.metrics._
import zio.metrics.connectors.prometheus.PrometheusPublisher
import zio.metrics.connectors.{MetricsConfig, prometheus}

object HelloWorldWithMetrics extends ZIOAppDefault {

  val backend: HttpApp[Any] =
    Routes(
      Method.GET / "json"      -> handler((req: Request) =>
        ZIO.succeed(Response.json("""{"message": "Hello World!"}""")) @@ Metric
          .counter("x_custom_header_total")
          .contramap[Any](_ => if (req.headers.contains("X-Custom-Header")) 1L else 0L),
      ),
      Method.GET / "forbidden" -> handler(ZIO.succeed(Response.forbidden)),
    ).toHttpApp @@ Middleware.metrics()

  val metrics: HttpApp[PrometheusPublisher] =
    Routes(
      Method.GET / "metrics" -> handler(ZIO.serviceWithZIO[PrometheusPublisher](_.get.map(Response.text))),
    ).toHttpApp

  val run =
    Server
      .serve(backend ++ metrics)
      .provide(
        Server.default,
        // The prometheus reporting layer
        prometheus.prometheusLayer,
        prometheus.publisherLayer,
        // Interval for polling metrics
        ZLayer.succeed(MetricsConfig(1.seconds)),
      )
}
