//> using dep "dev.zio::zio-http:3.4.1"
//> using dep "dev.zio::zio-metrics-connectors-prometheus:2.3.1"

package example

import zio._
import zio.metrics._
import zio.metrics.connectors.prometheus.PrometheusPublisher
import zio.metrics.connectors.{MetricsConfig, prometheus}

import zio.http._

object HelloWorldWithMetrics extends ZIOAppDefault {

  val backend: Routes[Any, Response] =
    Routes(
      Method.GET / "json"      -> handler((req: Request) =>
        ZIO.succeed(Response.json("""{"message": "Hello World!"}""")) @@ Metric
          .counter("x_custom_header_total")
          .contramap[Any](_ => if (req.headers.contains("X-Custom-Header")) 1L else 0L),
      ),
      Method.GET / "forbidden" -> handler(ZIO.succeed(Response.forbidden)),
    ) @@ Middleware.metrics()

  val metrics: Routes[PrometheusPublisher, Response] =
    Routes(
      Method.GET / "metrics" -> handler(ZIO.serviceWithZIO[PrometheusPublisher](_.get.map(Response.text))),
    )

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
