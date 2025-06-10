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
    allowedOrigin: Header.Origin => Option[Header.AccessControlAllowOrigin] = origin =>
      Some(Header.AccessControlAllowOrigin.Specific(origin)),
    allowedMethods: Header.AccessControlAllowMethods = Header.AccessControlAllowMethods.All,
    allowedHeaders: Header.AccessControlAllowHeaders = Header.AccessControlAllowHeaders.All,
    allowCredentials: Header.AccessControlAllowCredentials = Header.AccessControlAllowCredentials.Allow,
    exposedHeaders: Header.AccessControlExposeHeaders = Header.AccessControlExposeHeaders.All,
    maxAge: Option[Header.AccessControlMaxAge] = None,
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
    def allowedHeaders(
      requestedHeaders: Option[Header.AccessControlRequestHeaders],
      allowedHeaders: Header.AccessControlAllowHeaders,
    ): Header.AccessControlAllowHeaders =
      // Returning an intersection of requested headers and allowed headers
      // if there are no requested headers, we return the configured allowed headers without modification
      allowedHeaders match {
        case Header.AccessControlAllowHeaders.Some(values) =>
          requestedHeaders match {
            case Some(Header.AccessControlRequestHeaders(headers)) =>
              val intersection = headers.toSet.intersect(values.toSet)
              NonEmptyChunk.fromIterableOption(intersection) match {
                case Some(values) => Header.AccessControlAllowHeaders.Some(values)
                case None         => Header.AccessControlAllowHeaders.None
              }
            case None                                              => allowedHeaders
          }
        case Header.AccessControlAllowHeaders.All          =>
          requestedHeaders match {
            case Some(Header.AccessControlRequestHeaders(headers)) => Header.AccessControlAllowHeaders.Some(headers)
            case _                                                 => Header.AccessControlAllowHeaders.All
          }
        case Header.AccessControlAllowHeaders.None         => Header.AccessControlAllowHeaders.None
      }

    def corsHeaders(
      allowOrigin: Header.AccessControlAllowOrigin,
      requestedHeaders: Option[Header.AccessControlRequestHeaders],
      isPreflight: Boolean,
    ): Headers =
      Headers(
        allowOrigin,
        config.allowedMethods,
        config.allowCredentials,
      ) ++
        Headers.ifThenElse(isPreflight)(
          onTrue = Headers(allowedHeaders(requestedHeaders, config.allowedHeaders)),
          onFalse = Headers(config.exposedHeaders),
        ) ++ config.maxAge.fold(Headers.empty)(Headers(_))

    // HandlerAspect:
    val aspect =
      HandlerAspect.interceptHandlerStateful[Any, Headers, Unit](
        Handler.fromFunction[Request] { request =>
          val originHeader = request.header(Header.Origin)
          val acrhHeader   = request.header(Header.AccessControlRequestHeaders)

          originHeader match {
            case Some(origin) =>
              config.allowedOrigin(origin) match {
                case Some(allowOrigin) if config.allowedMethods.contains(request.method) =>
                  (corsHeaders(allowOrigin, acrhHeader, isPreflight = false), (request, ()))
                case _                                                                   =>
                  (Headers.empty, (request, ()))
              }

            case None => (Headers.empty, (request, ()))
          }
        },
      )(Handler.fromFunction[(Headers, Response)] { case (headers, response) =>
        response.addHeaders(headers)
      })

    val optionsRoute = {
      implicit val trace: Trace = Trace.empty

      Method.OPTIONS / trailing -> handler { (_: Path, request: Request) =>
        val originHeader = request.header(Header.Origin)
        val acrmHeader   = request.header(Header.AccessControlRequestMethod)
        val acrhHeader   = request.header(Header.AccessControlRequestHeaders)

        (
          originHeader,
          acrmHeader,
        ) match {
          case (Some(origin), Some(acrm)) =>
            config.allowedOrigin(origin) match {
              case Some(allowOrigin) if config.allowedMethods.contains(acrm.method) =>
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

  def ensureHeader(header: Header.HeaderType)(make: => header.HeaderValue): Middleware[Any] =
    new Middleware[Any] {
      def apply[Env1 <: Any, Err](routes: Routes[Env1, Err]): Routes[Env1, Err] =
        routes.transform[Env1] { h =>
          Handler.scoped[Env1] {
            handler { (req: Request) =>
              if (req.headers.contains(header.name)) h(req)
              else h(req.addHeader(make))
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
              else h(req.addHeader[String](headerName, make))
            }
          }
        }
    }

  private[http] case class ForwardedHeaders(headers: Headers)

  def forwardHeaders(header: Header.HeaderType, headers: Header.HeaderType*)(implicit
    trace: Trace,
  ): Middleware[Any] = {
    val allHeaders = header +: headers
    new Middleware[Any] {
      def apply[Env1 <: Any, Err](routes: Routes[Env1, Err]): Routes[Env1, Err] =
        routes.transform[Env1] { h =>
          Handler.scoped[Env1] {
            handler { (req: Request) =>
              val headerValues = ChunkBuilder.make[Header]()
              headerValues.sizeHint(allHeaders.length)
              var i            = 0
              while (i < allHeaders.length) {
                val name = allHeaders(i)
                req.headers.get(name).foreach { value =>
                  headerValues += value
                }
                i += 1
              }
              RequestStore.update[ForwardedHeaders] { old =>
                ForwardedHeaders {
                  old.map(_.headers).getOrElse(Headers.empty) ++
                    Headers.fromIterable(headerValues.result())
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
              val headerValues = ChunkBuilder.make[Header]()
              headerValues.sizeHint(allHeaders.length)
              var i            = 0
              while (i < allHeaders.length) {
                val name = allHeaders(i)
                req.headers.get(name).foreach { value =>
                  headerValues += Header.Custom(name, value)
                }
                i += 1
              }
              RequestStore.update[ForwardedHeaders] { old =>
                ForwardedHeaders {
                  old.map(_.headers).getOrElse(Headers.empty) ++
                    Headers.fromIterable(headerValues.result())
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
                annotations += LogAnnotation(name, req.headers.get(name).mkString)
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
  def logAnnotateHeaders(header: Header.HeaderType, headers: Header.HeaderType*)(implicit
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
        MetricLabel("method", routePattern.method.render),
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
        end <- Clock.nanoTime
        took = end - start
        _ <- requestDuration.tagged(labels).update(took / nanosToSeconds)
      } yield ()

    def aspect(routePattern: RoutePattern[_])(implicit trace: Trace): HandlerAspect[Any, Unit] =
      HandlerAspect.interceptHandlerStateful(Handler.fromFunctionZIO[Request] { req =>
        val requestLabels = labelsForRequest(routePattern)

        for {
          start <- Clock.nanoTime
          _     <- concurrentRequests.tagged(requestLabels).increment
        } yield ((start, requestLabels), (req, ()))
      })(Handler.fromFunctionZIO[((Long, Set[MetricLabel]), Response)] { case ((start, requestLabels), response) =>
        val allLabels = requestLabels ++ labelsForResponse(response)

        report(start, requestLabels, allLabels).as(response)
      })

    new Middleware[Any] {
      def apply[Env1, Err](routes: Routes[Env1, Err]): Routes[Env1, Err] =
        Routes.fromIterable(
          routes.routes.map(route => route.transform[Env1](_ @@ aspect(route.routePattern))),
        )
    }
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
          Method.GET / path.segments.map(PathCodec.literal).reduceLeftOption(_ / _).getOrElse(PathCodec.empty)
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
                val path   = request.path.unnest(unnest).addLeadingSlash
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
    req.cookie(Flash.COOKIE_NAME).fold(resp)(flash => resp.addCookie(Cookie.clear(flash.name)))
  }

}
