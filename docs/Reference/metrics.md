---
id: metrics
title: "Metrics reference"
---

### Reference on metrics

1. APIs and Classes:
   - `zio.metrics.Metric`: Provides APIs for creating and managing metrics.
     - `Metric.counterInt(name: String): Counter[RuntimeFlags]`: Creates a counter metric of type `Int` with the given name.
     - `Metric.gauge(name: String): Gauge[Double]`: Creates a gauge metric of type `Double` with the given name.
     - `Metric.histogram(name: String, boundaries: MetricKeyType.Histogram.Boundaries): Histogram[Double]`: Creates a histogram metric of type `Double` with the given name and boundaries.
   - `zio.metrics.MetricLabel`: Represents a label associated with a metric.

2. Functions:
   - `metrics`: A function that adds metrics to a ZIO-HTTP server.
     - Parameters:
       - `pathLabelMapper: PartialFunction[Request, String] = Map.empty`: A mapping function to map incoming paths to patterns.
       - `concurrentRequestsName: String = "http_concurrent_requests_total"`: Name of the concurrent requests metric.
       - `totalRequestsName: String = "http_requests_total"`: Name of the total requests metric.
       - `requestDurationName: String = "http_request_duration_seconds"`: Name of the request duration metric.
       - `requestDurationBoundaries: MetricKeyType.Histogram.Boundaries = Metrics.defaultBoundaries`: Boundaries for the request duration metric.
       - `extraLabels: Set[MetricLabel] = Set.empty`: A set of extra labels that will be tagged with all metrics.
     - Returns: An `HttpAppMiddleware` that adds metrics to the server.

3. Usage Example:
```scala
import zio.http.{RequestHandlerMiddlewares, _}
import zio.metrics.Metric.{Counter, Gauge, Histogram}
import zio.metrics.{Metric, MetricKeyType, MetricLabel}

private[zio] trait Metrics { self: RequestHandlerMiddlewares =>
  // ...

  def metrics(
    pathLabelMapper: PartialFunction[Request, String] = Map.empty,
    concurrentRequestsName: String = "http_concurrent_requests_total",
    totalRequestsName: String = "http_requests_total",
    requestDurationName: String = "http_request_duration_seconds",
    requestDurationBoundaries: MetricKeyType.Histogram.Boundaries = Metrics.defaultBoundaries,
    extraLabels: Set[MetricLabel] = Set.empty,
  ): HttpAppMiddleware[Nothing, Any, Nothing, Any] = {
    // ...
  }

  // ...
}

object Metrics {
  // ...
}
```

To use the `metrics` function, you can create an instance of a `Metrics` object and call the `metrics` method, providing the desired parameters. Here's an example:

```scala
import zio.http.HttpAppMiddleware.metrics

val app: HttpApp[Any, Nothing] = ???
val metricsMiddleware = new Metrics with RequestHandlerMiddlewares {}
val middleware = metricsMiddleware.metrics(
  pathLabelMapper = { case Method.GET -> Root / "user" / _ =>
    "/user/:id"
  },
  extraLabels = Set(MetricLabel("test", "http_requests_total with path label mapper")),
)
val appWithMetrics = middleware(app)
```

This example creates an HTTP app `app` and applies the `metrics` middleware with custom parameters. The `pathLabelMapper` is used to map specific paths to patterns, and extra labels are provided. The resulting `appWithMetrics` is the original app with the metrics middleware applied.