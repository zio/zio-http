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

import java.io.{PrintWriter, StringWriter}
import java.nio.charset._

import zio._
import zio.metrics._

import zio.http.html._
import zio.http.internal.middlewares.{Auth, Metrics}

/**
 * A [[zio.http.Middleware]] is a kind of [[zio.http.ProtocolStack]] that is
 * specialized to transform a handler's incoming requests and outgoing
 * responses. Each layer in the stack corresponds to a separate transformation.
 *
 * Layers may incorporate layer-specific information into a generic type
 * parameter, referred to as middleware context, which composes using tupling.
 *
 * Layers may also be stateful at the level of each transformation application.
 * So, for example, a layer that is timing request durations may capture the
 * start time of the request in the incoming interceptor, and pass this state to
 * the outgoing interceptor, which can then compute the duration.
 *
 * The [[zio.http.Middleware]] is more than just a wrapper around
 * [[zio.http.ProtocolStack]], as its concatenation operator has been
 * specialized to entuple contexts, so that each layer may only add context to
 * the contextual output.
 */
final case class Middleware[-Env, +CtxOut](
  protocol: ProtocolStack[
    Env,
    Request,
    (Request, CtxOut),
    Response,
    Response,
  ],
) { self =>
  def ++[Env1 <: Env, CtxOut2](
    that: Middleware[Env1, CtxOut2],
  )(implicit zippable: Zippable[CtxOut, CtxOut2]): Middleware[Env1, zippable.Out] =
    Middleware {
      val combiner: ProtocolStack[Env1, (Request, CtxOut), (Request, zippable.Out), Response, Response] =
        ProtocolStack.interceptHandlerStateful[
          Env1,
          that.protocol.State,
          (Request, CtxOut),
          (Request, zippable.Out),
          Response,
          Response,
        ](
          Handler.fromFunctionZIO[(Request, CtxOut)] { tuple =>
            that.protocol.incoming(tuple._1).map { case (state, (request, env)) =>
              (state, (request, zippable.zip(tuple._2, env)))
            }
          },
        )(
          Handler.fromFunctionZIO[(that.protocol.State, Response)] { case (state, either) =>
            that.protocol.outgoing(state, either)
          },
        )

      self.protocol ++ combiner
    }

  def as[CtxOut2](ctxOut2: => CtxOut2): Middleware[Env, CtxOut2] =
    map(_ => ctxOut2)

  def map[CtxOut2](f: CtxOut => CtxOut2): Middleware[Env, CtxOut2] =
    Middleware(protocol.mapIncoming { case (request, ctx) => (request, f(ctx)) })

  def unit: Middleware[Env, Unit] = as(())

  def whenHeader(condition: Headers => Boolean): Middleware[Env, Unit] =
    Middleware.whenHeader(condition)(self.unit)

  def whenRequest(condition: Request => Boolean): Middleware[Env, Unit] =
    Middleware.whenRequest(condition)(self.unit)

  def whenRequestZIO[Env1 <: Env](condition: Request => ZIO[Env1, Response, Boolean]): Middleware[Env1, Unit] =
    Middleware.whenRequestZIO(condition)(self.unit)
}
object Middleware extends zio.http.internal.HeaderModifier[Middleware[Any, Unit]] {

  /**
   * Sets cookie in response headers
   */
  def addCookie(cookie: Cookie.Response): Middleware[Any, Unit] =
    addHeader(Header.SetCookie(cookie))

  def addCookieZIO[Env](cookie: ZIO[Env, Nothing, Cookie.Response])(implicit
    trace: Trace,
  ): Middleware[Env, Unit] =
    updateResponseZIO(response => cookie.map(response.addCookie))

  /**
   * Creates a middleware for basic authentication
   */
  def basicAuth(f: Auth.Credentials => Boolean): Middleware[Any, Unit] =
    customAuth(
      _.header(Header.Authorization) match {
        case Some(Header.Authorization.Basic(userName, password)) =>
          f(Auth.Credentials(userName, password))
        case _                                                    => false
      },
      Headers(Header.WWWAuthenticate.Basic()),
    )

  /**
   * Creates a middleware for basic authentication that checks if the
   * credentials are same as the ones given
   */
  def basicAuth(u: String, p: String): Middleware[Any, Unit] =
    basicAuth { credentials => (credentials.uname == u) && (credentials.upassword == p) }

  /**
   * Creates a middleware for basic authentication using an effectful
   * verification function
   */
  def basicAuthZIO[Env](f: Auth.Credentials => ZIO[Env, Response, Boolean])(implicit
    trace: Trace,
  ): Middleware[Env, Unit] =
    customAuthZIO(
      _.header(Header.Authorization) match {
        case Some(Header.Authorization.Basic(userName, password)) =>
          f(Auth.Credentials(userName, password))
        case _                                                    => ZIO.succeed(false)
      },
      Headers(Header.WWWAuthenticate.Basic()),
    )

  /**
   * Creates a middleware for bearer authentication that checks the token using
   * the given function
   * @param f:
   *   function that validates the token string inside the Bearer Header
   */
  def bearerAuth(f: String => Boolean): Middleware[Any, Unit] =
    customAuth(
      _.header(Header.Authorization) match {
        case Some(Header.Authorization.Bearer(token)) => f(token)
        case _                                        => false
      },
      Headers(Header.WWWAuthenticate.Bearer(realm = "Access")),
    )

  /**
   * Creates a middleware for bearer authentication that checks the token using
   * the given effectful function
   * @param f:
   *   function that effectfully validates the token string inside the Bearer
   *   Header
   */
  def bearerAuthZIO[Env](
    f: String => ZIO[Env, Response, Boolean],
  )(implicit trace: Trace): Middleware[Env, Unit] =
    customAuthZIO(
      _.header(Header.Authorization) match {
        case Some(Header.Authorization.Bearer(token)) => f(token)
        case _                                        => ZIO.succeed(false)
      },
      Headers(Header.WWWAuthenticate.Bearer(realm = "Access")),
    )

  /**
   * Beautify the error response.
   */
  def beautifyErrors: Middleware[Any, Unit] =
    intercept(replaceErrorResponse)

  /**
   * Creates an authentication middleware that only allows authenticated
   * requests to be passed on to the app.
   */
  def customAuth(
    verify: Request => Boolean,
    responseHeaders: Headers = Headers.empty,
    responseStatus: Status = Status.Unauthorized,
  ): Middleware[Any, Unit] =
    Middleware.interceptIncomingHandler[Any, Unit] {
      Handler.fromFunctionExit[Request] { request =>
        if (verify(request)) Exit.succeed(request -> ())
        else Exit.fail(Response.status(responseStatus).addHeaders(responseHeaders))
      }
    }

  /**
   * Creates an authentication middleware that only allows authenticated
   * requests to be passed on to the app, and provides a context to the request
   * handlers.
   */
  final def customAuthProviding[Context](
    provide: Request => Option[Context],
    responseHeaders: Headers = Headers.empty,
    responseStatus: Status = Status.Unauthorized,
  ): Middleware[Any, Context] =
    customAuthProvidingZIO((request: Request) => Exit.succeed(provide(request)), responseHeaders, responseStatus)

  /**
   * Creates an authentication middleware that only allows authenticated
   * requests to be passed on to the app, and provides a context to the request
   * handlers.
   */
  def customAuthProvidingZIO[Env, Context](
    provide: Request => ZIO[Env, Nothing, Option[Context]],
    responseHeaders: Headers = Headers.empty,
    responseStatus: Status = Status.Unauthorized,
  ): Middleware[Env, Context] =
    Middleware.interceptIncomingHandler[Env, Context](
      Handler.fromFunctionZIO[Request] { req =>
        provide(req).flatMap {
          case Some(context) => ZIO.succeed((req, context))
          case None          => ZIO.fail(Response.status(responseStatus).addHeaders(responseHeaders))
        }
      },
    )

  /**
   * Creates an authentication middleware that only allows authenticated
   * requests to be passed on to the app using an effectful verification
   * function.
   */
  def customAuthZIO[Env](
    verify: Request => ZIO[Env, Response, Boolean],
    responseHeaders: Headers = Headers.empty,
    responseStatus: Status = Status.Unauthorized,
  ): Middleware[Env, Unit] =
    Middleware.interceptIncomingHandler[Env, Unit](Handler.fromFunctionZIO[Request] { request =>
      verify(request).flatMap {
        case true  => ZIO.succeed((request, ()))
        case false => ZIO.fail(Response.status(responseStatus).addHeaders(responseHeaders))
      }
    })

  def debug: Middleware[Any, Unit] =
    Middleware.interceptHandlerStateful(Handler.fromFunctionZIO[Request] { request =>
      zio.Clock.instant.map(now => ((now, request), (request, ())))
    })(Handler.fromFunctionZIO[((java.time.Instant, Request), Response)] { case ((start, request), response) =>
      zio.Clock.instant.flatMap { end =>
        val duration = java.time.Duration.between(start, end)

        Console
          .printLine(s"${response.status.code} ${request.method} ${request.url.encode} ${duration.toMillis}ms")
          .orDie
          .as(response)
      }
    })

  def fail(
    response: Response,
  ): Middleware[Any, Unit] =
    Middleware(ProtocolStack.interceptHandler(Handler.fail(response))(Handler.identity))

  def failWith(f: Request => Response): Middleware[Any, Unit] =
    Middleware(
      ProtocolStack.interceptHandler(Handler.fromFunctionExit[Request](in => Exit.fail(f(in))))(Handler.identity),
    )

  val identity: Middleware[Any, Unit] =
    interceptHandler[Any, Unit](Handler.identity[Request].map(_ -> ()))(Handler.identity)

  def ifHeaderThenElse[Env, Ctx](
    condition: Headers => Boolean,
  )(
    ifTrue: Middleware[Env, Ctx],
    ifFalse: Middleware[Env, Ctx],
  ): Middleware[Env, Ctx] =
    ifRequestThenElse(request => condition(request.headers))(ifTrue, ifFalse)

  def ifMethodThenElse[Env, Ctx](
    condition: Method => Boolean,
  )(
    ifTrue: Middleware[Env, Ctx],
    ifFalse: Middleware[Env, Ctx],
  ): Middleware[Env, Ctx] =
    ifRequestThenElse(request => condition(request.method))(ifTrue, ifFalse)

  def ifRequestThenElse[Env, CtxOut](
    predicate: Request => Boolean,
  )(
    ifTrue: Middleware[Env, CtxOut],
    ifFalse: Middleware[Env, CtxOut],
  ): Middleware[Env, CtxOut] = {
    val ifTrue2  = ifTrue.protocol
    val ifFalse2 = ifFalse.protocol

    Middleware(ProtocolStack.cond[Request](req => predicate(req))(ifTrue2, ifFalse2))
  }

  def ifRequestThenElseZIO[Env, CtxOut](
    predicate: Request => ZIO[Env, Response, Boolean],
  )(
    ifTrue: Middleware[Env, CtxOut],
    ifFalse: Middleware[Env, CtxOut],
  ): Middleware[Env, CtxOut] = {
    val ifTrue2  = ifTrue.protocol
    val ifFalse2 = ifFalse.protocol

    Middleware(ProtocolStack.condZIO[Request](req => predicate(req))(ifTrue2, ifFalse2))
  }

  def intercept(
    fromRequestAndResponse: (Request, Response) => Response,
  ): Middleware[Any, Unit] =
    interceptHandlerStateful(Handler.identity[Request].map(req => (req, (req, ()))))(
      Handler.fromFunction[(Request, Response)] { case (req, res) => fromRequestAndResponse(req, res) },
    )

  def interceptHandler[Env, CtxOut](
    incoming0: Handler[Env, Response, Request, (Request, CtxOut)],
  )(
    outgoing0: Handler[Env, Nothing, Response, Response],
  ): Middleware[Env, CtxOut] =
    Middleware[Env, CtxOut](ProtocolStack.interceptHandler(incoming0)(outgoing0))

  def interceptHandlerStateful[Env, State0, CtxOut](
    incoming0: Handler[
      Env,
      Response,
      Request,
      (State0, (Request, CtxOut)),
    ],
  )(
    outgoing0: Handler[Env, Nothing, (State0, Response), Response],
  ): Middleware[Env, CtxOut] =
    Middleware[Env, CtxOut](ProtocolStack.interceptHandlerStateful(incoming0)(outgoing0))

  def interceptIncomingHandler[Env, CtxOut](
    handler: Handler[Env, Response, Request, (Request, CtxOut)],
  ): Middleware[Env, CtxOut] =
    interceptHandler(handler)(Handler.identity)

  def interceptOutgoingHandler[Env](
    handler: Handler[Env, Nothing, Response, Response],
  ): Middleware[Env, Unit] =
    interceptHandler[Env, Unit](Handler.identity[Request].map(_ -> ()))(handler)

  /**
   * Creates a new middleware using transformation functions
   */
  def interceptPatch[S](fromRequest: Request => S): InterceptPatch[S] = new InterceptPatch[S](fromRequest)

  /**
   * Creates a new middleware using effectful transformation functions
   */
  def interceptPatchZIO[Env, S](fromRequest: Request => ZIO[Env, Response, S]): InterceptPatchZIO[Env, S] =
    new InterceptPatchZIO[Env, S](fromRequest)

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
    requestDurationBoundaries: MetricKeyType.Histogram.Boundaries = defaultBoundaries,
    extraLabels: Set[MetricLabel] = Set.empty,
  ): Middleware[Any, Unit] = {
    val requestsTotal: Metric.Counter[RuntimeFlags] = Metric.counterInt(totalRequestsName)
    val concurrentRequests: Metric.Gauge[Double]    = Metric.gauge(concurrentRequestsName)
    val requestDuration: Metric.Histogram[Double]   = Metric.histogram(requestDurationName, requestDurationBoundaries)
    val nanosToSeconds: Double                      = 1e9d

    def labelsForRequest(req: Request): Set[MetricLabel] =
      Set(
        MetricLabel("method", req.method.toString),
        MetricLabel("path", pathLabelMapper.lift(req).getOrElse(req.path.toString())),
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

    Middleware.interceptHandlerStateful(Handler.fromFunctionZIO[Request] { req =>
      val requestLabels = labelsForRequest(req)

      for {
        start <- Clock.nanoTime
        _     <- concurrentRequests.tagged(requestLabels).increment
      } yield ((start, requestLabels), (req, ()))
    })(Handler.fromFunctionZIO[((Long, Set[MetricLabel]), Response)] { case ((start, requestLabels), response) =>
      val allLabels = requestLabels ++ labelsForResponse(response)

      report(start, requestLabels, allLabels).as(response)
    })
  }

  /**
   * Creates a middleware that produces a Patch for the Response
   */
  def patch(f: Response => Response.Patch): Middleware[Any, Unit] =
    interceptPatch(_ => ())((response, _) => f(response))

  /**
   * Creates a middleware that produces a Patch for the Response effectfully.
   */
  def patchZIO[Env](f: Response => ZIO[Env, Response, Response.Patch]): Middleware[Env, Unit] =
    interceptPatchZIO[Env, Unit](_ => ZIO.unit)((response, _) => f(response))

  def redirect(url: URL, isPermanent: Boolean): Middleware[Any, Unit] =
    fail(Response.redirect(url, isPermanent))

  def redirectTrailingSlash(
    isPermanent: Boolean,
  ): Middleware[Any, Unit] =
    ifRequestThenElse(request => request.url.path.hasTrailingSlash && request.url.queryParams.isEmpty)(
      ifTrue = Middleware.identity,
      ifFalse = updatePath(_.dropTrailingSlash) ++ failWith(request => Response.redirect(request.url, isPermanent)),
    )

  def requestLogging(
    level: Status => LogLevel = (_: Status) => LogLevel.Info,
    failureLevel: LogLevel = LogLevel.Warning,
    loggedRequestHeaders: Set[Header.HeaderType] = Set.empty,
    loggedResponseHeaders: Set[Header.HeaderType] = Set.empty,
    logRequestBody: Boolean = false,
    logResponseBody: Boolean = false,
    requestCharset: Charset = StandardCharsets.UTF_8,
    responseCharset: Charset = StandardCharsets.UTF_8,
  )(implicit trace: Trace): Middleware[Any, Unit] = {
    val loggedRequestHeaderNames  = loggedRequestHeaders.map(_.name.toLowerCase)
    val loggedResponseHeaderNames = loggedResponseHeaders.map(_.name.toLowerCase)

    Middleware.interceptHandlerStateful(Handler.fromFunctionZIO[Request] { request =>
      zio.Clock.instant.map(now => ((now, request), (request, ())))
    })(
      Handler.fromFunctionZIO[((java.time.Instant, Request), Response)] { case ((start, request), response) =>
        zio.Clock.instant.flatMap { end =>
          val duration = java.time.Duration.between(start, end)

          ZIO
            .logLevel(level(response.status)) {
              val requestHeaders  =
                request.headers.collect {
                  case header: Header if loggedRequestHeaderNames.contains(header.headerName.toLowerCase) =>
                    LogAnnotation(header.headerName, header.renderedValue)
                }.toSet
              val responseHeaders =
                response.headers.collect {
                  case header: Header if loggedResponseHeaderNames.contains(header.headerName.toLowerCase) =>
                    LogAnnotation(header.headerName, header.renderedValue)
                }.toSet

              val requestBody  = if (request.body.isComplete) request.body.asChunk.option else ZIO.none
              val responseBody = if (response.body.isComplete) response.body.asChunk.option else ZIO.none

              requestBody.flatMap { requestBodyChunk =>
                responseBody.flatMap { responseBodyChunk =>
                  val bodyAnnotations = Set(
                    requestBodyChunk.map(chunk => LogAnnotation("request_size", chunk.size.toString)),
                    requestBodyChunk.flatMap(chunk =>
                      if (logRequestBody)
                        Some(LogAnnotation("request", new String(chunk.toArray, requestCharset)))
                      else None,
                    ),
                    responseBodyChunk.map(chunk => LogAnnotation("response_size", chunk.size.toString)),
                    responseBodyChunk.flatMap(chunk =>
                      if (logResponseBody)
                        Some(LogAnnotation("response", new String(chunk.toArray, responseCharset)))
                      else None,
                    ),
                  ).flatten

                  ZIO.logAnnotate(
                    Set(
                      LogAnnotation("status_code", response.status.text),
                      LogAnnotation("method", request.method.toString()),
                      LogAnnotation("url", request.url.encode),
                      LogAnnotation("duration_ms", duration.toMillis.toString),
                    ) union
                      requestHeaders union
                      responseHeaders union
                      bodyAnnotations,
                  ) {
                    ZIO.log("Http request served").as(response)
                  }
                }
              }
            }
        }
      },
    )
  }

  def runAfter[Env](effect: ZIO[Env, Nothing, Any])(implicit trace: Trace): Middleware[Env, Unit] =
    updateResponseZIO(response => effect.as(response))

  def runBefore[Env](effect: ZIO[Env, Nothing, Any])(implicit trace: Trace): Middleware[Env, Unit] =
    updateRequestZIO(request => effect.as(request))

  /**
   * Creates a middleware for signing cookies
   */
  def signCookies(secret: String): Middleware[Any, Unit] =
    updateHeaders { headers =>
      headers.modify {
        case Header.SetCookie(cookie)                                                      =>
          Header.SetCookie(cookie.sign(secret))
        case header @ Header.Custom(name, value) if name.toString == Header.SetCookie.name =>
          Header.SetCookie.parse(value.toString) match {
            case Left(_)               => header
            case Right(responseCookie) => Header.SetCookie(responseCookie.value.sign(secret))
          }
        case header: Header                                                                => header
      }
    }

  def status(status: Status): Middleware[Any, Unit] =
    patch(_ => Response.Patch.status(status))

  override def updateHeaders(update: Headers => Headers): Middleware[Any, Unit] =
    updateResponse(_.updateHeaders(update))

  def updateMethod(update: Method => Method): Middleware[Any, Unit] =
    updateRequest(request => request.copy(method = update(request.method)))

  def updatePath(update: Path => Path): Middleware[Any, Unit] =
    updateRequest(request => request.copy(url = request.url.copy(path = update(request.url.path))))

  def updateRequest(update: Request => Request): Middleware[Any, Unit] =
    Middleware.interceptIncomingHandler {
      Handler.fromFunction[Request] { request =>
        (update(request), ())
      }
    }

  def updateRequestZIO[Env](update: Request => ZIO[Env, Response, Request]): Middleware[Env, Unit] =
    Middleware.interceptIncomingHandler {
      Handler.fromFunctionZIO[Request] { request =>
        update(request).map((_, ()))
      }
    }

  def updateResponse(update: Response => Response): Middleware[Any, Unit] =
    Middleware.interceptOutgoingHandler(Handler.fromFunction(update))

  def updateResponseZIO[Env](update: Response => ZIO[Env, Nothing, Response]): Middleware[Env, Unit] =
    Middleware.interceptOutgoingHandler(Handler.fromFunctionZIO[Response](update))

  def updateURL(update: URL => URL): Middleware[Any, Unit] =
    updateRequest(request => request.copy(url = update(request.url)))

  def whenHeader[Env](condition: Headers => Boolean)(
    middleware: Middleware[Env, Unit],
  ): Middleware[Env, Unit] =
    ifHeaderThenElse(condition)(ifFalse = identity, ifTrue = middleware)

  /**
   * Applies the middleware only if the condition function evaluates to true
   */
  def whenResponse(condition: Response => Boolean)(
    f: Response => Response,
  ): Middleware[Any, Unit] =
    Middleware(
      ProtocolStack.interceptHandler(Handler.identity[Request].map(_ -> ()))(Handler.fromFunctionZIO[Response] {
        response =>
          if (condition(response)) ZIO.succeed(f(response)) else ZIO.succeed(response)
      }),
    )

  /**
   * Applies the middleware only if the condition function effectfully evaluates
   * to true
   */
  def whenResponseZIO[Env](condition: Response => ZIO[Env, Response, Boolean])(
    f: Response => ZIO[Env, Response, Response],
  ): Middleware[Env, Unit] =
    Middleware(
      ProtocolStack.interceptHandler[Env, Request, (Request, Unit), Response, Response](
        Handler.identity[Request].map(_ -> ()),
      )(Handler.fromFunctionZIO[Response] { response =>
        condition(response)
          .flatMap[Env, Response, Response](bool => if (bool) f(response) else ZIO.succeed(response))
          .merge
      }),
    )

  /**
   * Applies the middleware only if the condition function evaluates to true
   */
  def whenRequest[Env](condition: Request => Boolean)(
    middleware: Middleware[Env, Unit],
  ): Middleware[Env, Unit] =
    ifRequestThenElse(condition)(ifFalse = identity, ifTrue = middleware)

  def whenRequestZIO[Env](condition: Request => ZIO[Env, Response, Boolean])(
    middleware: Middleware[Env, Unit],
  ): Middleware[Env, Unit] =
    ifRequestThenElseZIO(condition)(ifFalse = identity, ifTrue = middleware)

  final class InterceptPatch[State](val fromRequest: Request => State) extends AnyVal {
    def apply(result: (Response, State) => Response.Patch): Middleware[Any, Unit] =
      Middleware.interceptHandlerStateful(
        Handler.fromFunction[Request] { request =>
          val state = fromRequest(request)
          (state, (request, ()))
        },
      )(
        Handler.fromFunction[(State, Response)] { case (state, response) => response.patch(result(response, state)) },
      )
  }

  final class InterceptPatchZIO[Env, State](val fromRequest: Request => ZIO[Env, Response, State]) extends AnyVal {
    def apply(result: (Response, State) => ZIO[Env, Response, Response.Patch]): Middleware[Env, Unit] =
      Middleware.interceptHandlerStateful(
        Handler.fromFunctionZIO[Request] { request =>
          fromRequest(request).map((_, (request, ())))
        },
      )(
        Handler.fromFunctionZIO[(State, Response)] { case (state, response) =>
          result(response, state).map(response.patch(_)).merge
        },
      )
  }

  private def replaceErrorResponse(request: Request, response: Response): Response = {
    def htmlResponse: Body = {
      val message: String = response.httpError.map(_.message).getOrElse("")
      val data            = Template.container(s"${response.status}") {
        div(
          div(
            styles := Seq("text-align" -> "center"),
            div(s"${response.status.code}", styles := Seq("font-size" -> "20em")),
            div(message),
          ),
          div(
            response.httpError.get.foldCause(div()) { throwable =>
              div(h3("Cause:"), pre(prettify(throwable)))
            },
          ),
        )
      }
      Body.fromString("<!DOCTYPE html>" + data.encode)
    }

    def textResponse: Body = {
      Body.fromString(formatErrorMessage(response))
    }

    if (response.status.isError) {
      request.header(Header.Accept) match {
        case Some(value) if value.mimeTypes.exists(_.mediaType == MediaType.text.`html`) =>
          response.copy(
            body = htmlResponse,
            headers = Headers(Header.ContentType(MediaType.text.`html`)),
          )
        case Some(value) if value.mimeTypes.exists(_.mediaType == MediaType.any)         =>
          response.copy(
            body = textResponse,
            headers = Headers(Header.ContentType(MediaType.text.`plain`)),
          )
        case _                                                                           => response
      }

    } else
      response
  }

  private def prettify(throwable: Throwable): String = {
    val sw = new StringWriter
    throwable.printStackTrace(new PrintWriter(sw))
    s"${sw.toString}"
  }

  private def formatCause(response: Response): String =
    response.httpError.get.foldCause("") { throwable =>
      s"${scala.Console.BOLD}Cause: ${scala.Console.RESET}\n ${prettify(throwable)}"
    }

  private def formatErrorMessage(response: Response) = {
    val errorMessage: String = response.httpError.map(_.message).getOrElse("")
    val status               = response.status.code
    s"${scala.Console.BOLD}${scala.Console.RED}${response.status} ${scala.Console.RESET} - " +
      s"${scala.Console.BOLD}${scala.Console.CYAN}$status ${scala.Console.RESET} - " +
      s"$errorMessage\n${formatCause(response)}"
  }

  private val defaultBoundaries = MetricKeyType.Histogram.Boundaries.fromChunk(
    Chunk(
      .005, .01, .025, .05, .075, .1, .25, .5, .75, 1, 2.5, 5, 7.5, 10,
    ),
  )
}
