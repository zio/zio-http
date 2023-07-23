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
import zio.metrics.Metric.{Counter, Gauge, Histogram}
import zio.metrics.{Metric, MetricKeyType, MetricLabel}

import zio.http.{RequestHandlerMiddlewares, _}

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
  ): HttpAppMiddleware[Nothing, Any, Nothing, Any] = {
    new HttpAppMiddleware.Simple[Any, Nothing] {
      val requestsTotal: Counter[RuntimeFlags] = Metric.counterInt(totalRequestsName)
      val concurrentRequests: Gauge[Double]    = Metric.gauge(concurrentRequestsName)
      val requestDuration: Histogram[Double]   = Metric.histogram(requestDurationName, requestDurationBoundaries)
      val status404: Set[MetricLabel]          = Set(MetricLabel("status", "404"))
      val status500: Set[MetricLabel]          = Set(MetricLabel("status", "500"))
      val nanosToSeconds: Double               = 1e9d

      private def labelsWithoutPathForRequest(req: Request): Set[MetricLabel] =
        extraLabels + MetricLabel("method", req.method.toString)

      private def labelsWithPathForRequest(req: Request): Set[MetricLabel] =
        labelsWithoutPathForRequest(req) + MetricLabel("path", pathLabelMapper.lift(req).getOrElse(req.path.toString()))

      private def labelsForResponse(res: Response): Set[MetricLabel] =
        Set(
          MetricLabel("status", res.status.code.toString),
        )

      private def statusLabelForError(err: Any): Set[MetricLabel] =
        err match {
          case response: Response => Set(MetricLabel("status", response.status.code.toString))
          case _                  => status500
        }

      private def report(
        start: Long,
        requestLabels: Set[MetricLabel],
        labels: Set[MetricLabel],
      )(implicit trace: Trace): ZIO[Any, Nothing, Unit] =
        for {
          _   <- requestsTotal.tagged(labels).increment
          _   <- concurrentRequests.tagged(requestLabels).decrement
          end <- Clock.nanoTime
          took = end - start
          _ <- requestDuration.tagged(labels).update(took / nanosToSeconds)
        } yield ()

      override def apply[R1, Err1](
        http: HttpApp[R1, Err1],
      )(implicit trace: Trace): HttpApp[R1, Err1] =
        Http.fromOptionalHandlerZIO[Request] { req =>
          def onHandlerNotFound(start: Long): ZIO[Any, Option[Nothing], Handler[R1, Err1, Request, Response]] = {
            val requestLabels = labelsWithoutPathForRequest(req)

            concurrentRequests.tagged(requestLabels).increment *> report(
              start,
              requestLabels,
              requestLabels ++ status404,
            ) *> ZIO.fail(None)
          }

          def onHandler(
            start: Long,
            handler: Handler[R1, Err1, Request, Response],
          ): ZIO[Any, Option[Nothing], Handler[R1, Err1, Request, Response]] = {
            val requestLabels = labelsWithPathForRequest(req)

            concurrentRequests.tagged(requestLabels).increment *>
              ZIO.succeed {
                handler.onExit { exit =>
                  val labels =
                    requestLabels ++ exit.foldExit(
                      cause => cause.failureOption.fold(status500)(statusLabelForError),
                      labelsForResponse,
                    )

                  report(start, requestLabels, labels)
                }
              }
          }

          for {
            start           <- Clock.nanoTime
            optionalHandler <- http.runHandler(req)
            handler         <- optionalHandler.fold(onHandlerNotFound(start))(onHandler(start, _))
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
