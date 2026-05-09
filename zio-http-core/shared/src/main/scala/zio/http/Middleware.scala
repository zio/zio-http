/*
 * Copyright 2023 the ZIO HTTP contributors.
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
package zio.http

import java.io.File
import java.nio.charset.Charset

import scala.annotation.nowarn

import zio._
import zio.metrics._

import zio.http.codec.{PathCodec, SegmentCodec}
import zio.http.headers.{AccessControlAllowCredentials, AccessControlAllowHeaders, AccessControlAllowMethods, AccessControlAllowOrigin, AccessControlExposeHeaders, AccessControlMaxAge, AccessControlRequestHeaders, AccessControlRequestMethod, Origin}

@nowarn("msg=shadows type")
trait Middleware[-UpperEnv] { self =>

  def apply[Env1 <: UpperEnv, Err](routes: Routes[Env1, Err]): Routes[Env1, Err]

  def @@[UpperEnv1 <: UpperEnv](
    that: Middleware[UpperEnv1],
  ): Middleware[UpperEnv1] =
    self ++ that

  def ++[UpperEnv1 <: UpperEnv](
    that: Middleware[UpperEnv1],
  ): Middleware[UpperEnv1] =
    new Middleware[UpperEnv1] {
      def apply[Env1 <: UpperEnv1, Err](
        routes: Routes[Env1, Err],
      ): Routes[Env1, Err] =
        self(that(routes))
    }
}

@nowarn("msg=shadows type")
object Middleware extends HandlerAspects {

  final protected override def addHeader(name: CharSequence, value: CharSequence): HandlerAspect[Any, Unit] =
    HandlerAspect.addHeader[String](name.toString, value.toString)

  /**
   * Configuration for the CORS aspect.
   */
  final case class CorsConfig(
    allowedOrigin: Origin => Option[AccessControlAllowOrigin] = origin =>
      Some(AccessControlAllowOrigin.Specific(origin.renderedValue)),
    allowedMethods: AccessControlAllowMethods = AccessControlAllowMethods(
      zio.blocks.chunk.Chunk.fromArray(
        Array(Method.GET, Method.POST, Method.PUT, Method.DELETE, Method.PATCH, Method.HEAD, Method.OPTIONS),
      ),
    ),
    allowedHeaders: AccessControlAllowHeaders = AccessControlAllowHeaders(
      zio.blocks.chunk.Chunk.fromArray(Array("*")),
    ),
    allowCredentials: AccessControlAllowCredentials = AccessControlAllowCredentials(true),
    exposedHeaders: AccessControlExposeHeaders = AccessControlExposeHeaders(
      zio.blocks.chunk.Chunk.fromArray(Array("*")),
    ),
    maxAge: Option[AccessControlMaxAge] = None,
  )

  /**
   * Creates a aspect for Cross-Origin Resource Sharing (CORS) using default
   * options.
   * @see
   *   https://developer.mozilla.org/en-US/docs/Web/HTTP/CORS
   */
  def cors: Middleware[Any] = cors(CorsConfig())

  /**
   * Creates a aspect for Cross-Origin Resource Sharing (CORS).
   * @see
   *   https://developer.mozilla.org/en-US/docs/Web/HTTP/CORS
   */
  def cors(config: CorsConfig): Middleware[Any] = {
    // Pre-build the allowed headers Set once at construction time to avoid per-request allocation
    val allowedHeadersSet: Set[String] = {
      val h = config.allowedHeaders.headers
      if (h.length == 1 && h(0) == "*") Set.empty
      else {
        val s = Set.newBuilder[String]
        var i = 0
        while (i < h.length) { s += h(i); i += 1 }
        s.result()
      }
    }

    // Pre-build fixed headers at construction time to avoid per-request allocation.
    // The repetition of config fields across branches is intentional: each call to
    // Headers(...) produces a flat FromIterable, avoiding any Concat wrapper that
    // Headers#++ would introduce.
    val (nonPreflightHeaders, preflightBaseHeaders): (Headers, Headers) =
      config.maxAge match {
        case Some(maxAge) =>
          (
            Headers(
              config.allowedMethods.headerName   -> config.allowedMethods.renderedValue,
              config.allowCredentials.headerName -> config.allowCredentials.renderedValue,
              config.exposedHeaders.headerName   -> config.exposedHeaders.renderedValue,
              maxAge.headerName                  -> maxAge.renderedValue,
            ),
            Headers(
              config.allowedMethods.headerName   -> config.allowedMethods.renderedValue,
              config.allowCredentials.headerName -> config.allowCredentials.renderedValue,
              maxAge.headerName                  -> maxAge.renderedValue,
            ),
          )
        case None         =>
          (
            Headers(
              config.allowedMethods.headerName   -> config.allowedMethods.renderedValue,
              config.allowCredentials.headerName -> config.allowCredentials.renderedValue,
              config.exposedHeaders.headerName   -> config.exposedHeaders.renderedValue,
            ),
            Headers(
              config.allowedMethods.headerName   -> config.allowedMethods.renderedValue,
              config.allowCredentials.headerName -> config.allowCredentials.renderedValue,
            ),
          )
      }

    def allowedHeaders(
      requestedHeaders: Option[AccessControlRequestHeaders],
      allowedHeaders: AccessControlAllowHeaders,
    ): AccessControlAllowHeaders = {
      val isWildcard = allowedHeaders.headers.length == 1 && allowedHeaders.headers(0) == "*"
      if (isWildcard) {
        requestedHeaders match {
          case Some(AccessControlRequestHeaders(headers)) =>
            AccessControlAllowHeaders(headers)
          case _                                                 => allowedHeaders
        }
      } else if (allowedHeaders.headers.isEmpty) {
        allowedHeaders // empty = none
      } else {
        requestedHeaders match {
          case Some(AccessControlRequestHeaders(headers)) =>
            val intersection = {
              val requested = new scala.collection.mutable.ArrayBuffer[String]()
              var i         = 0
              while (i < headers.length) { requested += headers(i); i += 1 }
              requested.toSet.intersect(allowedHeadersSet)
            }
            if (intersection.nonEmpty)
              AccessControlAllowHeaders(zio.blocks.chunk.Chunk.fromArray(intersection.toArray))
            else
              AccessControlAllowHeaders(zio.blocks.chunk.Chunk.empty)
          case None                                              => allowedHeaders
        }
      }
    }

    def corsHeaders(
      allowOrigin: AccessControlAllowOrigin,
      requestedHeaders: Option[AccessControlRequestHeaders],
      isPreflight: Boolean,
    ): Headers =
      if (isPreflight) {
        val ah = allowedHeaders(requestedHeaders, config.allowedHeaders)
        Headers(
          allowOrigin.headerName -> allowOrigin.renderedValue,
          ah.headerName          -> ah.renderedValue,
        ) ++ preflightBaseHeaders
      } else
        Headers(allowOrigin.headerName -> allowOrigin.renderedValue) ++ nonPreflightHeaders

    def methodAllowed(method: Method): Boolean = {
      val methods = config.allowedMethods.methods
      var found   = false
      var i       = 0
      while (i < methods.length && !found) { if (methods(i) == method) found = true; i += 1 }
      found
    }

    // HandlerAspect:
    val aspect =
      HandlerAspect.interceptHandlerStateful[Any, Headers, Unit](
        Handler.fromFunctionZIO[Request] { request =>
          val originHeader = request.header(Origin)
          val acrhHeader   = request.header(AccessControlRequestHeaders)

          originHeader match {
            case Some(origin) =>
              config.allowedOrigin(origin) match {
                case Some(allowOrigin) if methodAllowed(request.method) =>
                  ZIO.succeed((corsHeaders(allowOrigin, acrhHeader, isPreflight = false), (request, ())))
                case _                                                  =>
                  ZIO.fail(Response(status = Status.Forbidden))
              }

            case None => ZIO.succeed((Headers.empty, (request, ())))
          }
        },
      )(Handler.fromFunction[(Headers, Response)] { case (headers, response) =>
        response.addHeaders(headers)
      })

    val optionsRoute = {
      implicit val trace: Trace = Trace.empty

      RoutePattern.OPTIONS / trailing -> handler { (_: Path, request: Request) =>
        val originHeader = request.header(Origin)
        val acrmHeader   = request.header(AccessControlRequestMethod)
        val acrhHeader   = request.header(AccessControlRequestHeaders)

        (
          originHeader,
          acrmHeader,
        ) match {
          case (Some(origin), Some(acrm)) =>
            config.allowedOrigin(origin) match {
              case Some(allowOrigin) if methodAllowed(acrm.method) =>
                Response(
                  Status.NoContent,
                  headers = corsHeaders(allowOrigin, acrhHeader, isPreflight = true),
                )

              case _ =>
                Response.notFound
            }
          case _                          =>
            Response.notFound
        }
      }
    }

    new Middleware[Any] {
      def apply[Env1, Err](routes: Routes[Env1, Err]): Routes[Env1, Err] =
        optionsRoute +: (routes @@ aspect)
    }
  }

  def ensureHeader[H <: Header](header: Header.Typed[H])(make: => H): Middleware[Any] =
    new Middleware[Any] {
      def apply[Env1 <: Any, Err](routes: Routes[Env1, Err]): Routes[Env1, Err] =
        routes.transform[Env1] { h =>
          Handler.scoped[Env1] {
            handler { (req: Request) =>
              if (req.headers.contains(header.name)) h(req)
              else h(req.addHeader(make.headerName, make.renderedValue))
            }
          }
        }
    }

  def ensureHeader(headerName: String)(make: => String): Middleware[Any] =
    new Middleware[Any] {
      def apply[Env1 <: Any, Err](routes: Routes[Env1, Err]): Routes[Env1, Err] =
        routes.transform[Env1] { h =>
          Handler.scoped[Env1] {
            handler { (req: Request) =>
              if (req.headers.contains(headerName)) h(req)
              else h(req.addHeader(headerName, make))
            }
          }
        }
    }

  private[http] case class ForwardedHeaders(headers: Headers)

  def forwardHeaders(f: Headers => Headers)(implicit trace: Trace): Middleware[Any] =
    new Middleware[Any] {
      def apply[Env1 <: Any, Err](routes: Routes[Env1, Err]): Routes[Env1, Err] =
        routes.transform[Env1] { h =>
          Handler.scoped[Env1] {
            handler { (req: Request) =>
              val headerValues = f(req.headers)
              RequestStore.update[ForwardedHeaders] { old =>
                ForwardedHeaders {
                  old.map(_.headers).getOrElse(Headers.empty) ++
                    headerValues
                }
              } *> h(req)
            }
          }
        }
    }

  def forwardHeaders(header: Header.Typed[_ <: Header], headers: Header.Typed[_ <: Header]*)(implicit
    trace: Trace,
  ): Middleware[Any] = {
    val allHeaders = header +: headers
    new Middleware[Any] {
      def apply[Env1 <: Any, Err](routes: Routes[Env1, Err]): Routes[Env1, Err] =
        routes.transform[Env1] { h =>
          Handler.scoped[Env1] {
            handler { (req: Request) =>
              val pairs = scala.collection.mutable.ListBuffer.empty[(String, String)]
              var i     = 0
              while (i < allHeaders.length) {
                val ht = allHeaders(i)
                req.headers.get(ht).foreach { value =>
                  pairs += ((value.headerName, value.renderedValue))
                }
                i += 1
              }
              RequestStore.update[ForwardedHeaders] { old =>
                ForwardedHeaders {
                  old.map(_.headers).getOrElse(Headers.empty) ++
                    Headers(pairs.toSeq: _*)
                }
              } *> h(req)
            }
          }
        }
    }
  }

  def forwardHeaders(headerName: String, headerNames: String*)(implicit trace: Trace): Middleware[Any] = {
    val allHeaders = headerName +: headerNames
    new Middleware[Any] {
      def apply[Env1 <: Any, Err](routes: Routes[Env1, Err]): Routes[Env1, Err] =
        routes.transform[Env1] { h =>
          Handler.scoped[Env1] {
            handler { (req: Request) =>
              val pairs = scala.collection.mutable.ListBuffer.empty[(String, String)]
              val headerList = req.headers.toList
              var i          = 0
              while (i < allHeaders.length) {
                val name = allHeaders(i)
                headerList.foreach { case (n, v) =>
                  if (n.equalsIgnoreCase(name)) pairs += ((n, v))
                }
                i += 1
              }
              RequestStore.update[ForwardedHeaders] { old =>
                ForwardedHeaders {
                  old.map(_.headers).getOrElse(Headers.empty) ++
                    Headers(pairs.toSeq: _*)
                }
              } *> h(req)
            }
          }
        }
    }
  }

  def logAnnotate(key: => String, value: => String)(implicit trace: Trace): Middleware[Any] =
    logAnnotate(LogAnnotation(key, value))

  def logAnnotate(logAnnotation: => LogAnnotation, logAnnotations: LogAnnotation*)(implicit
    trace: Trace,
  ): Middleware[Any] =
    logAnnotate((logAnnotation +: logAnnotations).toSet)

  def logAnnotate(logAnnotations: => Set[LogAnnotation])(implicit trace: Trace): Middleware[Any] =
    new Middleware[Any] {
      def apply[Env1 <: Any, Err](routes: Routes[Env1, Err]): Routes[Env1, Err] =
        routes.transform[Env1] { h =>
          Handler.scoped[Env1](handler((req: Request) => ZIO.logAnnotate(logAnnotations)(h(req))))

        }
    }

  def logAnnotateZIO[Env](
    logAnnotations: => URIO[Env, Set[LogAnnotation]],
  )(implicit trace: Trace): Middleware[Env] =
    new Middleware[Env] {
      def apply[Env1 <: Env, Err](routes: Routes[Env1, Err]): Routes[Env1, Err] =
        routes.transform[Env1] { h =>
          Handler.scoped[Env1](handler((req: Request) => logAnnotations.flatMap(ZIO.logAnnotate(_)(h(req)))))
        }
    }

  /**
   * Creates a middleware that will annotate log messages that are logged while
   * a request is handled with log annotations derived from the request.
   */
  def logAnnotate(fromRequest: Request => Set[LogAnnotation])(implicit trace: Trace): Middleware[Any] =
    new Middleware[Any] {
      def apply[Env1 <: Any, Err](routes: Routes[Env1, Err]): Routes[Env1, Err] =
        routes.transform[Env1] { h =>
          Handler.scoped[Env1](handler((req: Request) => ZIO.logAnnotate(fromRequest(req))(h(req))))
        }
    }

  /**
   * Creates a middleware that will annotate log messages that are logged while
   * a request is handled with the names and the values of the specified
   * headers.
   */
  def logAnnotateHeaders(headerName: String, headerNames: String*)(implicit trace: Trace): Middleware[Any] =
    new Middleware[Any] {
      def apply[Env1 <: Any, Err](routes: Routes[Env1, Err]): Routes[Env1, Err] = {
        val headers = headerName +: headerNames
        routes.transform[Env1] { h =>
          Handler.scoped[Env1] {
            handler((req: Request) => {
              val annotations = Set.newBuilder[LogAnnotation]
              annotations.sizeHint(headers.length)
              var i           = 0
              while (i < headers.length) {
                val name = headers(i)
                annotations += LogAnnotation(name, req.headers.rawGet(name).mkString)
                i += 1
              }
              ZIO.logAnnotate(annotations.result())(h(req))
            })
          }
        }
      }
    }

  /**
   * Creates middleware that will annotate log messages that are logged while a
   * request is handled with the names and the values of the specified headers.
   */
  def logAnnotateHeaders(header: Header.Typed[_ <: Header], headers: Header.Typed[_ <: Header]*)(implicit
    trace: Trace,
  ): Middleware[Any] =
    logAnnotateHeaders(header.name, headers.map(_.name): _*)

  def timeout(duration: Duration)(implicit trace: Trace): Middleware[Any] =
    new Middleware[Any] {
      def apply[Env1 <: Any, Err](routes: Routes[Env1, Err]): Routes[Env1, Err] =
        routes.transform[Env1] { handler =>
          handler.timeoutFail(Response(status = Status.RequestTimeout))(duration)
        }
    }

  /**
   * Creates middleware that will track metrics.
   *
   * @param totalRequestsName
   *   Total HTTP requests metric name.
   * @param requestDurationName
   *   HTTP request duration metric name.
   * @param requestDurationBoundaries
   *   Boundaries for the HTTP request duration metric.
   * @param extraLabels
   *   A set of extra labels all metrics will be tagged with.
   */
  def metrics(
    concurrentRequestsName: String = "http_concurrent_requests_total",
    totalRequestsName: String = "http_requests_total",
    requestDurationName: String = "http_request_duration_seconds",
    requestDurationBoundaries: MetricKeyType.Histogram.Boundaries = defaultBoundaries,
    extraLabels: Set[MetricLabel] = Set.empty,
  )(implicit trace: Trace): Middleware[Any] = {
    val requestsTotal: Metric.Counter[RuntimeFlags] = Metric.counterInt(totalRequestsName)
    val concurrentRequests: Metric.Gauge[Double]    = Metric.gauge(concurrentRequestsName)
    val requestDuration: Metric.Histogram[Double]   = Metric.histogram(requestDurationName, requestDurationBoundaries)
    val nanosToSeconds: Double                      = 1e9d

    def labelsForRequest(routePattern: RoutePattern[_]): Set[MetricLabel] =
      Set(
        MetricLabel("method", routePattern.method.name),
        MetricLabel("path", routePattern.pathCodec.render),
      ) ++ extraLabels

    def labelsForResponse(res: Response): Set[MetricLabel] =
      Set(
        MetricLabel("status", res.status.code.toString),
      )

    def report(
      start: Long,
      requestLabels: Set[MetricLabel],
      labels: Set[MetricLabel],
    )(implicit trace: Trace): ZIO[Any, Nothing, Unit] =
      for {
        _   <- requestsTotal.tagged(labels).increment
        _   <- concurrentRequests.tagged(requestLabels).decrement
        end <- ZIO.succeed(java.lang.System.nanoTime())
        took = end - start
        _ <- requestDuration.tagged(labels).update(took / nanosToSeconds)
      } yield ()

    def aspect(routePattern: RoutePattern[_])(implicit trace: Trace): HandlerAspect[Any, Unit] = {
      // Computed once at route registration time, not per request
      val requestLabels = labelsForRequest(routePattern)

      HandlerAspect.interceptHandlerStateful(Handler.fromFunctionZIO[Request] { req =>
        for {
          start <- ZIO.succeed(java.lang.System.nanoTime())
          _     <- concurrentRequests.tagged(requestLabels).increment
        } yield ((start, requestLabels), (req, ()))
      })(Handler.fromFunctionZIO[((Long, Set[MetricLabel]), Response)] { case ((start, requestLabels), response) =>
        val allLabels = requestLabels ++ labelsForResponse(response)

        report(start, requestLabels, allLabels).as(response)
      })
    }

    new Middleware[Any] {
      def apply[Env1, Err](routes: Routes[Env1, Err]): Routes[Env1, Err] =
        Routes.fromIterable(
          routes.routes.map(route => route.transform[Env1](handler => handler.sandbox @@ aspect(route.routePattern))),
        )
    }
  }

  /**
   * Creates middleware that will track metrics, with additional labels derived
   * from the response.
   *
   * @param responseLabels
   *   A pure function that derives additional metric labels from the response
   *   (e.g. status class, specific headers). Invoked once per response.
   * @param concurrentRequestsName
   *   Concurrent HTTP requests metric name.
   * @param totalRequestsName
   *   Total HTTP requests metric name.
   * @param requestDurationName
   *   HTTP request duration metric name.
   * @param requestDurationBoundaries
   *   Boundaries for the HTTP request duration metric.
   * @param extraLabels
   *   A set of extra labels all metrics will be tagged with.
   */
  def metrics(
    responseLabels: Response => Set[MetricLabel],
    pathLabelMapper: String => String,
    concurrentRequestsName: String,
    totalRequestsName: String,
    requestDurationName: String,
    requestDurationBoundaries: MetricKeyType.Histogram.Boundaries,
    extraLabels: Set[MetricLabel],
  )(implicit trace: Trace): Middleware[Any] = {
    val requestsTotal: Metric.Counter[RuntimeFlags] = Metric.counterInt(totalRequestsName)
    val concurrentRequests: Metric.Gauge[Double]    = Metric.gauge(concurrentRequestsName)
    val requestDuration: Metric.Histogram[Double]   = Metric.histogram(requestDurationName, requestDurationBoundaries)
    val nanosToSeconds: Double                      = 1e9d

    def labelsForRequest(routePattern: RoutePattern[_]): Set[MetricLabel] =
      Set(
        MetricLabel("method", routePattern.method.name),
        MetricLabel("path", pathLabelMapper(routePattern.pathCodec.render)),
      ) ++ extraLabels

    def labelsForResponse(res: Response): Set[MetricLabel] =
      Set(
        MetricLabel("status", res.status.code.toString),
      ) ++ responseLabels(res)

    def report(
      start: Long,
      requestLabels: Set[MetricLabel],
      labels: Set[MetricLabel],
    )(implicit trace: Trace): ZIO[Any, Nothing, Unit] =
      for {
        _   <- requestsTotal.tagged(labels).increment
        _   <- concurrentRequests.tagged(requestLabels).decrement
        end <- ZIO.succeed(java.lang.System.nanoTime())
        took = end - start
        _ <- requestDuration.tagged(labels).update(took / nanosToSeconds)
      } yield ()

    def aspect(routePattern: RoutePattern[_])(implicit trace: Trace): HandlerAspect[Any, Unit] = {
      val requestLabels = labelsForRequest(routePattern)

      HandlerAspect.interceptHandlerStateful(Handler.fromFunctionZIO[Request] { req =>
        for {
          start <- ZIO.succeed(java.lang.System.nanoTime())
          _     <- concurrentRequests.tagged(requestLabels).increment
        } yield ((start, requestLabels), (req, ()))
      })(Handler.fromFunctionZIO[((Long, Set[MetricLabel]), Response)] { case ((start, requestLabels), response) =>
        val allLabels = requestLabels ++ labelsForResponse(response)

        report(start, requestLabels, allLabels).as(response)
      })
    }

    new Middleware[Any] {
      def apply[Env1, Err](routes: Routes[Env1, Err]): Routes[Env1, Err] =
        Routes.fromIterable(
          routes.routes.map(route => route.transform[Env1](handler => handler.sandbox @@ aspect(route.routePattern))),
        )
    }
  }

  /**
   * Creates middleware that will track metrics, with additional labels derived
   * from the response. Uses default metric names and boundaries.
   *
   * @param responseLabels
   *   A pure function that derives additional metric labels from the response
   *   (e.g. status class, specific headers). Invoked once per response.
   * @param extraLabels
   *   A set of extra labels all metrics will be tagged with.
   */
  def metrics(
    responseLabels: Response => Set[MetricLabel],
    pathLabelMapper: String => String,
    extraLabels: Set[MetricLabel],
  )(implicit trace: Trace): Middleware[Any] =
    metrics(
      responseLabels = responseLabels,
      pathLabelMapper = pathLabelMapper,
      concurrentRequestsName = "http_concurrent_requests_total",
      totalRequestsName = "http_requests_total",
      requestDurationName = "http_request_duration_seconds",
      requestDurationBoundaries = defaultBoundaries,
      extraLabels = extraLabels,
    )

  /**
   * Creates middleware for HTTP request tracing using ZIO's built-in log spans
   * and annotations. When used with a ZIO OpenTelemetry backend, spans are
   * automatically exported as OpenTelemetry traces.
   *
   * @param spanName
   *   Derives the span name from the route pattern and request. Defaults to
   *   "{METHOD} {route}" per OpenTelemetry semantic conventions.
   * @param additionalAttributes
   *   Derives additional log annotations from the request.
   */
  def tracing(
    spanName: (RoutePattern[_], Request) => String = (routePattern, request) =>
      s"${request.method.name} ${routePattern.pathCodec.render}",
    additionalAttributes: Request => Set[LogAnnotation] = _ => Set.empty,
  )(implicit trace: Trace): Middleware[Any] =
    new Middleware[Any] {
      def apply[Env1 <: Any, Err](routes: Routes[Env1, Err]): Routes[Env1, Err] =
        Routes.fromIterable(
          routes.routes.map { route =>
            val routePattern = route.routePattern
            route.transform[Env1] { h =>
              Handler.scoped[Env1](handler { (request: Request) =>
                val name        = spanName(routePattern, request)
                val annotations = Set(
                  LogAnnotation("http.method", request.method.name),
                  LogAnnotation("http.route", routePattern.pathCodec.render),
                  LogAnnotation("http.target", request.url.path.encode + request.url.queryParams.encode),
                ) ++ additionalAttributes(request)

                ZIO.logSpan(name) {
                  ZIO.logAnnotate(annotations) {
                    h(request)
                  }
                }
              })
            }
          },
        )
    }

  private sealed trait StaticServe[-R, +E] { self =>
    def run(path: Path, req: Request): Handler[R, E, Request, Response]

  }

  private object StaticServe {
    def make[R, E](f: (Path, Request) => Handler[R, E, Request, Response]): StaticServe[R, E] =
      new StaticServe[R, E] {
        override def run(path: Path, request: Request) = f(path, request)
      }

    def fromDirectory(docRoot: File)(implicit trace: Trace): StaticServe[Any, Throwable] = make { (path, _) =>
      val target = new File(docRoot.getAbsolutePath + path.encode)
      if (target.getCanonicalPath.startsWith(docRoot.getCanonicalPath))
        Handler.fromFile(target, Charset.defaultCharset())
      else {
        Handler.fromZIO(
          ZIO.logWarning(s"attempt to access file outside of docRoot: ${target.getAbsolutePath}"),
        ) *> Handler.badRequest
      }
    }

    def fromResource(resourcePrefix: String)(implicit trace: Trace): StaticServe[Any, Throwable] = make { (path, _) =>
      // validate that resourcePrefix starts with an optional slash, followed by at least 1 java identifier character
      val rp = if (resourcePrefix.startsWith("/")) resourcePrefix else "/" + resourcePrefix
      if (rp.length < 2 || !Character.isJavaIdentifierStart(rp.charAt(1))) {
        Handler.die(new IllegalArgumentException("resourcePrefix must have at least 1 valid character"))
      } else {
        Handler.fromResource(s"${resourcePrefix}/${path.dropLeadingSlash.encode}")
      }
    }

  }

  private def toMiddleware[E](path: Path, staticServe: StaticServe[Any, E])(implicit trace: Trace): Middleware[Any] =
    new Middleware[Any] {

      private def checkFishy(acc: Boolean, segment: String): Boolean = {
        val stop = segment.indexOf('/') >= 0 || segment.indexOf('\\') >= 0 || segment == ".."
        acc || stop
      }

      override def apply[Env1 <: Any, Err](routes: Routes[Env1, Err]): Routes[Env1, Err] = {
        val mountpoint =
          RoutePattern.GET / path.segments.map(PathCodec.literal).reduceLeftOption(_ / _).getOrElse(PathCodec.empty)
        val pattern    = mountpoint / trailing
        val other      = Routes(
          pattern -> Handler
            .identity[Request]
            .flatMap { request =>
              val isFishy = request.path.segments.foldLeft(false)(checkFishy)
              if (isFishy) {
                Handler.fromZIO(ZIO.logWarning(s"fishy request detected: ${request.path.encode}")) *> Handler.badRequest
              } else {
                val segs   = pattern.pathCodec.segments.collect { case SegmentCodec.Literal(v) =>
                  v
                }
                val unnest = segs.foldLeft(Path.empty)(_ / _).addLeadingSlash
                val path   = request.path.drop(unnest.length).addLeadingSlash
                staticServe.run(path, request).sandbox
              }
            },
        )
        routes ++ other
      }

    }

  /**
   * Creates a middleware for serving static files from the directory `docRoot`
   * at the url path `path`.
   *
   * Example: `val serveDirectory = Middleware.serveDirectory(Path.empty /
   * "assets", new File("/some/local/path"))`
   *
   * With this middleware in place, a request to
   * `https://www.domain.com/assets/folder/file1.jpg` would serve the local file
   * `/some/local/path/folder/file1.jpg`.
   */
  def serveDirectory(path: Path, docRoot: File)(implicit trace: Trace): Middleware[Any] =
    toMiddleware(path, StaticServe.fromDirectory(docRoot))

  /**
   * Creates a middleware for serving static files at URL path `path` from
   * resources with the given `resourcePrefix`.
   *
   * Example: `Middleware.serveResources(Path.empty / "assets", "webapp")`
   *
   * With this middleware in place, a request to
   * `https://www.domain.com/assets/folder/file1.jpg` would serve the file
   * `src/main/resources/webapp/folder/file1.jpg`. Note how the URL path is
   * removed and the resourcePrefix prepended.
   *
   * Most build systems support resources in the `src/main/resources` directory.
   * In the above example, the file `src/main/resources/webapp/folder/file1.jpg`
   * would be served.
   *
   * * The `resourcePrefix` defaults to `"public"`. To prevent insecure sharing
   * of * resource files, `resourcePrefix` must start with a `/` followed by at
   * least 1 *
   * [[java.lang.Character.isJavaIdentifierStart(x\$1:Char)* valid java identifier character]].
   * The `/` * will be prepended if it is not present.
   */
  def serveResources(path: Path, resourcePrefix: String = "public")(implicit trace: Trace): Middleware[Any] =
    toMiddleware(path, StaticServe.fromResource(resourcePrefix))

  /**
   * Creates a middleware for managing the flash scope.
   */
  def flashScopeHandling: HandlerAspect[Any, Unit] = Middleware.intercept { (req, resp) =>
    val cookieHeader = req.headers.rawGet("cookie")
    val flashCookie = cookieHeader.flatMap { raw =>
      Cookie.parseRequest(raw).toList.find(_.name == Flash.COOKIE_NAME)
    }
    flashCookie.fold(resp) { flash =>
      resp.addCookie(ResponseCookie(flash.name, "", maxAge = Some(0L)))
    }
  }

}
