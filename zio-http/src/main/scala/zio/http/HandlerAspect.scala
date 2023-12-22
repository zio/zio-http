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

import zio.http.template._

/**
 * A [[zio.http.HandlerAspect]] is a kind of [[zio.http.ProtocolStack]] that is
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
 * [[zio.http.HandlerAspect]] is more than just a wrapper around
 * [[zio.http.ProtocolStack]], as its concatenation operator has been
 * specialized to entuple contexts, so that each layer may only add context to
 * the contextual output.
 */
final case class HandlerAspect[-Env, +CtxOut](
  protocol: ProtocolStack[
    Env,
    Request,
    (Request, CtxOut),
    Response,
    Response,
  ],
) extends Middleware[Env] { self =>

  /**
   * Combines this middleware with the specified middleware sequentially, such
   * that this middleware will be applied first on incoming requests, and last
   * on outgoing responses, and the specified middleware will be applied last on
   * incoming requests, and first on outgoing responses. Context from both
   * middleware will be combined using tuples.
   */
  def ++[Env1 <: Env, CtxOut2](
    that: HandlerAspect[Env1, CtxOut2],
  )(implicit zippable: Zippable[CtxOut, CtxOut2]): HandlerAspect[Env1, zippable.Out] =
    HandlerAspect {
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

  /**
   * Applies middleware to the specified handler, which may ignore the context
   * produced by this middleware.
   */
  def apply[Env1 <: Env, Err](
    routes: Routes[Env1, Err],
  ): Routes[Env1, Err] =
    routes.transform[Env1] { handler =>
      if (self == HandlerAspect.identity) handler
      else {
        for {
          tuple <- protocol.incomingHandler
          (state, (request, ctxOut)) = tuple
          either   <- Handler.fromZIO(handler(request)).either
          response <- Handler.fromZIO(protocol.outgoingHandler((state, either.merge)))
          response <- if (either.isLeft) Handler.fail(response) else Handler.succeed(response)
        } yield response
      }
    }

  /**
   * Applies middleware to the specified handler, which must process the context
   * produced by this middleware.
   */
  def applyHandlerContext[Env1 <: Env](
    handler: Handler[Env1, Response, (CtxOut, Request), Response],
  ): Handler[Env1, Response, Request, Response] = {
    if (self == HandlerAspect.identity) handler.contramap[Request](req => (().asInstanceOf[CtxOut], req))
    else {
      for {
        tuple <- protocol.incomingHandler
        (state, (request, ctxOut)) = tuple
        either   <- Handler.fromZIO(handler((ctxOut, request))).either
        response <- Handler.fromZIO(protocol.outgoingHandler((state, either.merge)))
        response <- if (either.isLeft) Handler.fail(response) else Handler.succeed(response)
      } yield response
    }
  }

  def applyHandler[Env1 <: Env](handler: RequestHandler[Env1, Response]): RequestHandler[Env1, Response] =
    if (self == HandlerAspect.identity) handler
    else {
      for {
        tuple <- protocol.incomingHandler
        (state, (request, ctxOut)) = tuple
        either   <- Handler.fromZIO(handler(request)).either
        response <- Handler.fromZIO(protocol.outgoingHandler((state, either.merge)))
        response <- if (either.isLeft) Handler.fail(response) else Handler.succeed(response)
      } yield response
    }

  /**
   * Returns new middleware that transforms the context of the middleware to the
   * specified constant.
   */
  def as[CtxOut2](ctxOut2: => CtxOut2): HandlerAspect[Env, CtxOut2] =
    map(_ => ctxOut2)

  /**
   * Returns new middleware that transforms the context of the middleware using
   * the specified function.
   */
  def map[CtxOut2](f: CtxOut => CtxOut2): HandlerAspect[Env, CtxOut2] =
    HandlerAspect(protocol.mapIncoming { case (request, ctx) => (request, f(ctx)) })

  /**
   * Returns new middleware that fully provides the specified environment to
   * this middleware, resulting in middleware that has no contextual
   * dependencies.
   */
  def provideEnvironment(env: ZEnvironment[Env]): HandlerAspect[Any, CtxOut] =
    HandlerAspect(protocol.provideEnvironment(env))

  /**
   * Returns new middleware that produces the unit value as its context.
   */
  def unit: HandlerAspect[Env, Unit] = as(())

  /**
   * Conditionally applies this middleware to the specified handler, based on
   * the result of the predicate applied to the incoming request's headers.
   */
  def whenHeader(condition: Headers => Boolean): HandlerAspect[Env, Unit] =
    HandlerAspect.whenHeader(condition)(self.unit)

  /**
   * Conditionally applies this middleware to the specified handler, based on
   * the result of the predicate applied to the incoming request.
   */
  def when(condition: Request => Boolean): HandlerAspect[Env, Unit] =
    HandlerAspect.when(condition)(self.unit)

  /**
   * Conditionally applies this middleware to the specified handler, based on
   * the result of the effectful predicate applied to the incoming request.
   */
  def whenZIO[Env1 <: Env](condition: Request => ZIO[Env1, Response, Boolean]): HandlerAspect[Env1, Unit] =
    HandlerAspect.whenZIO(condition)(self.unit)
}
object HandlerAspect extends HandlerAspects {

  final class InterceptPatch[State](val fromRequest: Request => State) extends AnyVal {
    def apply(result: (Response, State) => Response.Patch): HandlerAspect[Any, Unit] =
      HandlerAspect.interceptHandlerStateful(
        Handler.fromFunction[Request] { request =>
          val state = fromRequest(request)
          (state, (request, ()))
        },
      )(
        Handler.fromFunction[(State, Response)] { case (state, response) => response.patch(result(response, state)) },
      )
  }

  final class InterceptPatchZIO[Env, State](val fromRequest: Request => ZIO[Env, Response, State]) extends AnyVal {
    def apply(result: (Response, State) => ZIO[Env, Response, Response.Patch]): HandlerAspect[Env, Unit] =
      HandlerAspect.interceptHandlerStateful(
        Handler.fromFunctionZIO[Request] { request =>
          fromRequest(request).map((_, (request, ())))
        },
      )(
        Handler.fromFunctionZIO[(State, Response)] { case (state, response) =>
          result(response, state).map(response.patch(_)).merge
        },
      )
  }

  final class Allow(val unit: Unit) extends AnyVal {
    def apply(condition: Request => Boolean): HandlerAspect[Any, Unit] =
      HandlerAspect.ifRequestThenElse(condition)(ifFalse = fail(Response.status(Status.Forbidden)), ifTrue = identity)
  }

  final class AllowZIO(val unit: Unit) extends AnyVal {
    def apply[Env](
      condition: Request => ZIO[Env, Response, Boolean],
    ): HandlerAspect[Env, Unit] =
      HandlerAspect.ifRequestThenElseZIO(condition)(
        ifFalse = fail(Response.status(Status.Forbidden)),
        ifTrue = identity,
      )
  }
}
private[http] trait HandlerAspects extends zio.http.internal.HeaderModifier[HandlerAspect[Any, Unit]] {

  /**
   * Sets a cookie in the response headers
   */
  def addCookie(cookie: Cookie.Response): HandlerAspect[Any, Unit] =
    addHeader(Header.SetCookie(cookie))

  /**
   * Sets an effectfully created cookie in the response headers.
   */
  def addCookieZIO[Env](cookie: ZIO[Env, Nothing, Cookie.Response])(implicit
    trace: Trace,
  ): HandlerAspect[Env, Unit] =
    updateResponseZIO(response => cookie.map(response.addCookie))

  /**
   * Creates a middleware which can allow or disallow access to an http based on
   * the predicate
   */
  def allow: HandlerAspect.Allow = new HandlerAspect.Allow(())

  /**
   * Creates a middleware which can allow or disallow access to an http based on
   * the effectful predicate.
   */
  def allowZIO: HandlerAspect.AllowZIO = new HandlerAspect.AllowZIO(())

  /**
   * Creates a middleware for basic authentication
   */
  def basicAuth(f: Credentials => Boolean): HandlerAspect[Any, Unit] =
    customAuth(
      _.header(Header.Authorization) match {
        case Some(Header.Authorization.Basic(userName, password)) =>
          f(Credentials(userName, password))
        case _                                                    => false
      },
      Headers(Header.WWWAuthenticate.Basic()),
    )

  /**
   * Creates a middleware for basic authentication that checks if the
   * credentials are same as the ones given
   */
  def basicAuth(u: String, p: String): HandlerAspect[Any, Unit] =
    basicAuth { credentials => (credentials.uname == u) && (credentials.upassword == p) }

  /**
   * Creates a middleware for basic authentication using an effectful
   * verification function
   */
  def basicAuthZIO[Env](f: Credentials => ZIO[Env, Response, Boolean])(implicit
    trace: Trace,
  ): HandlerAspect[Env, Unit] =
    customAuthZIO(
      _.header(Header.Authorization) match {
        case Some(Header.Authorization.Basic(userName, password)) =>
          f(Credentials(userName, password))
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
  def bearerAuth(f: String => Boolean): HandlerAspect[Any, Unit] =
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
  )(implicit trace: Trace): HandlerAspect[Env, Unit] =
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
  def beautifyErrors: HandlerAspect[Any, Unit] =
    intercept(replaceErrorResponse)

  /**
   * Creates an authentication middleware that only allows authenticated
   * requests to be passed on to the app.
   */
  def customAuth(
    verify: Request => Boolean,
    responseHeaders: Headers = Headers.empty,
    responseStatus: Status = Status.Unauthorized,
  ): HandlerAspect[Any, Unit] =
    HandlerAspect.interceptIncomingHandler[Any, Unit] {
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
  ): HandlerAspect[Any, Context] =
    customAuthProvidingZIO((request: Request) => Exit.succeed(provide(request)), responseHeaders, responseStatus)

  /**
   * Creates an authentication middleware that only allows authenticated
   * requests to be passed on to the app, and provides a context to the request
   * handlers.
   */
  def customAuthProvidingZIO[Env, Context](
    provide: Request => ZIO[Env, Response, Option[Context]],
    responseHeaders: Headers = Headers.empty,
    responseStatus: Status = Status.Unauthorized,
  ): HandlerAspect[Env, Context] =
    HandlerAspect.interceptIncomingHandler[Env, Context](
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
  ): HandlerAspect[Env, Unit] =
    HandlerAspect.interceptIncomingHandler[Env, Unit](Handler.fromFunctionZIO[Request] { request =>
      verify(request).flatMap {
        case true  => ZIO.succeed((request, ()))
        case false => ZIO.fail(Response.status(responseStatus).addHeaders(responseHeaders))
      }
    })

  /**
   * Creates middleware that debugs request and response.
   */
  def debug: HandlerAspect[Any, Unit] =
    HandlerAspect.interceptHandlerStateful(Handler.fromFunctionZIO[Request] { request =>
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

  /**
   * Creates middleware that drops trailing slashes from the request URL.
   */
  def dropTrailingSlash: HandlerAspect[Any, Unit] =
    dropTrailingSlash(onlyIfNoQueryParams = false)

  /**
   * Creates middleware that drops trailing slashes from the request URL.
   */
  def dropTrailingSlash(onlyIfNoQueryParams: Boolean): HandlerAspect[Any, Unit] =
    updateRequest { request =>
      if (!onlyIfNoQueryParams || request.url.queryParams.isEmpty) request.dropTrailingSlash else request
    }

  /**
   * Creates middleware that aborts the request with the specified response. No
   * downstream middleware will be invoked.
   */
  def fail(
    response: Response,
  ): HandlerAspect[Any, Unit] =
    HandlerAspect(ProtocolStack.interceptHandler(Handler.fail(response))(Handler.identity))

  /**
   * Creates middleware that aborts the request with the specified response. No
   * downstream middleware will be invoked.
   */
  def failWith(f: Request => Response): HandlerAspect[Any, Unit] =
    HandlerAspect(
      ProtocolStack.interceptHandler(Handler.fromFunctionExit[Request](in => Exit.fail(f(in))))(Handler.identity),
    )

  /**
   * The identity middleware, which has no effect on request or response.
   */
  val identity: HandlerAspect[Any, Unit] =
    interceptHandler[Any, Unit](Handler.identity[Request].map(_ -> ()))(Handler.identity)

  /**
   * Creates conditional middleware that switches between one middleware and
   * another based on the result of the predicate, applied to the incoming
   * request's headers.
   */
  def ifHeaderThenElse[Env, Ctx](
    condition: Headers => Boolean,
  )(
    ifTrue: HandlerAspect[Env, Ctx],
    ifFalse: HandlerAspect[Env, Ctx],
  ): HandlerAspect[Env, Ctx] =
    ifRequestThenElse(request => condition(request.headers))(ifTrue, ifFalse)

  /**
   * Creates conditional middleware that switches between one middleware and
   * another based on the result of the predicate, applied to the incoming
   * request's method.
   */
  def ifMethodThenElse[Env, Ctx](
    condition: Method => Boolean,
  )(
    ifTrue: HandlerAspect[Env, Ctx],
    ifFalse: HandlerAspect[Env, Ctx],
  ): HandlerAspect[Env, Ctx] =
    ifRequestThenElse(request => condition(request.method))(ifTrue, ifFalse)

  /**
   * Creates conditional middleware that switches between one middleware and
   * another based on the result of the predicate, applied to the incoming
   * request.
   */
  def ifRequestThenElse[Env, CtxOut](
    predicate: Request => Boolean,
  )(
    ifTrue: HandlerAspect[Env, CtxOut],
    ifFalse: HandlerAspect[Env, CtxOut],
  ): HandlerAspect[Env, CtxOut] = {
    val ifTrue2  = ifTrue.protocol
    val ifFalse2 = ifFalse.protocol

    HandlerAspect(ProtocolStack.cond[Request](req => predicate(req))(ifTrue2, ifFalse2))
  }

  /**
   * Creates conditional middleware that switches between one middleware and
   * another based on the result of the predicate, effectfully applied to the
   * incoming request.
   */
  def ifRequestThenElseZIO[Env, CtxOut](
    predicate: Request => ZIO[Env, Response, Boolean],
  )(
    ifTrue: HandlerAspect[Env, CtxOut],
    ifFalse: HandlerAspect[Env, CtxOut],
  ): HandlerAspect[Env, CtxOut] = {
    val ifTrue2  = ifTrue.protocol
    val ifFalse2 = ifFalse.protocol

    HandlerAspect(ProtocolStack.condZIO[Request](req => predicate(req))(ifTrue2, ifFalse2))
  }

  /**
   * Creates middleware that modifies the response, potentially using the
   * request.
   */
  def intercept(
    fromRequestAndResponse: (Request, Response) => Response,
  ): HandlerAspect[Any, Unit] =
    interceptHandlerStateful(Handler.identity[Request].map(req => (req, (req, ()))))(
      Handler.fromFunction[(Request, Response)] { case (req, res) => fromRequestAndResponse(req, res) },
    )

  /**
   * Creates middleware that will apply the specified stateless handlers to
   * incoming and outgoing requests. If the incoming handler fails, then the
   * outgoing handler will not be invoked.
   */
  def interceptHandler[Env, CtxOut](
    incoming0: Handler[Env, Response, Request, (Request, CtxOut)],
  )(
    outgoing0: Handler[Env, Nothing, Response, Response],
  ): HandlerAspect[Env, CtxOut] =
    HandlerAspect[Env, CtxOut](ProtocolStack.interceptHandler(incoming0)(outgoing0))

  /**
   * Creates middleware that will apply the specified stateful handlers to
   * incoming and outgoing requests. If the incoming handler fails, then the
   * outgoing handler will not be invoked.
   */
  def interceptHandlerStateful[Env, State0, CtxOut](
    incoming0: Handler[
      Env,
      Response,
      Request,
      (State0, (Request, CtxOut)),
    ],
  )(
    outgoing0: Handler[Env, Nothing, (State0, Response), Response],
  ): HandlerAspect[Env, CtxOut] =
    HandlerAspect[Env, CtxOut](ProtocolStack.interceptHandlerStateful(incoming0)(outgoing0))

  /**
   * Creates middleware that will apply the specified handler to incoming
   * requests.
   */
  def interceptIncomingHandler[Env, CtxOut](
    handler: Handler[Env, Response, Request, (Request, CtxOut)],
  ): HandlerAspect[Env, CtxOut] =
    interceptHandler(handler)(Handler.identity)

  /**
   * Creates middleware that will apply the specified handler to outgoing
   * responses.
   */
  def interceptOutgoingHandler[Env](
    handler: Handler[Env, Nothing, Response, Response],
  ): HandlerAspect[Env, Unit] =
    interceptHandler[Env, Unit](Handler.identity[Request].map(_ -> ()))(handler)

  /**
   * Creates a new middleware using transformation functions
   */
  def interceptPatch[S](fromRequest: Request => S): HandlerAspect.InterceptPatch[S] =
    new HandlerAspect.InterceptPatch[S](fromRequest)

  /**
   * Creates a new middleware using effectful transformation functions
   */
  def interceptPatchZIO[Env, S](
    fromRequest: Request => ZIO[Env, Response, S],
  ): HandlerAspect.InterceptPatchZIO[Env, S] =
    new HandlerAspect.InterceptPatchZIO[Env, S](fromRequest)

  /**
   * Creates a middleware that produces a Patch for the Response
   */
  def patch(f: Response => Response.Patch): HandlerAspect[Any, Unit] =
    interceptPatch(_ => ())((response, _) => f(response))

  /**
   * Creates a middleware that produces a Patch for the Response effectfully.
   */
  def patchZIO[Env](f: Response => ZIO[Env, Response, Response.Patch]): HandlerAspect[Env, Unit] =
    interceptPatchZIO[Env, Unit](_ => ZIO.unit)((response, _) => f(response))

  /**
   * Creates a middleware that will redirect requests to the specified URL.
   */
  def redirect(url: URL, isPermanent: Boolean = false): HandlerAspect[Any, Unit] =
    fail(Response.redirect(url, isPermanent))

  /**
   * Creates middleware that will redirect requests with trailing slash to the
   * same path without trailing slash.
   */
  def redirectTrailingSlash(
    isPermanent: Boolean = false,
  ): HandlerAspect[Any, Unit] =
    ifRequestThenElse(request => request.url.path.hasTrailingSlash && request.url.queryParams.isEmpty)(
      ifTrue = updatePath(_.dropTrailingSlash) ++ failWith(request => Response.redirect(request.url, isPermanent)),
      ifFalse = HandlerAspect.identity,
    )

  /**
   * Creates middleware that will perform request logging.
   */
  def requestLogging(
    level: Status => LogLevel = (_: Status) => LogLevel.Info,
    loggedRequestHeaders: Set[Header.HeaderType] = Set.empty,
    loggedResponseHeaders: Set[Header.HeaderType] = Set.empty,
    logRequestBody: Boolean = false,
    logResponseBody: Boolean = false,
    requestCharset: Charset = StandardCharsets.UTF_8,
    responseCharset: Charset = StandardCharsets.UTF_8,
  )(implicit trace: Trace): HandlerAspect[Any, Unit] = {
    val loggedRequestHeaderNames  = loggedRequestHeaders.map(_.name.toLowerCase)
    val loggedResponseHeaderNames = loggedResponseHeaders.map(_.name.toLowerCase)

    HandlerAspect.interceptHandlerStateful(Handler.fromFunctionZIO[Request] { request =>
      zio.Clock.instant.map(now => ((now, request), (request, ())))
    })(
      Handler.fromFunctionZIO[((java.time.Instant, Request), Response)] { case ((start, request), response) =>
        zio.Clock.instant.flatMap { end =>
          val duration = java.time.Duration.between(start, end)

          ZIO
            .logLevel(level(response.status)) {
              def requestHeaders  =
                request.headers.collect {
                  case header: Header if loggedRequestHeaderNames.contains(header.headerName.toLowerCase) =>
                    LogAnnotation(header.headerName, header.renderedValue)
                }.toSet
              def responseHeaders =
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

  /**
   * Creates middleware that will run the specified effect after every request.
   */
  def runAfter[Env](effect: ZIO[Env, Nothing, Any])(implicit trace: Trace): HandlerAspect[Env, Unit] =
    updateResponseZIO(response => effect.as(response))

  /**
   * Creates middleware that will run the specified effect before every request.
   */
  def runBefore[Env](effect: ZIO[Env, Nothing, Any])(implicit trace: Trace): HandlerAspect[Env, Unit] =
    updateRequestZIO(request => effect.as(request))

  /**
   * Creates a middleware for signing cookies
   */
  def signCookies(secret: String): HandlerAspect[Any, Unit] =
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

  /**
   * Creates middleware that will update the status of the response.
   */
  def status(status: Status): HandlerAspect[Any, Unit] =
    patch(_ => Response.Patch.status(status))

  /**
   * Creates middleware that will update the headers of the response.
   */
  override def updateHeaders(update: Headers => Headers)(implicit trace: Trace): HandlerAspect[Any, Unit] =
    updateResponse(_.updateHeaders(update))

  /**
   * Creates middleware that will update the method of the request.
   */
  def updateMethod(update: Method => Method): HandlerAspect[Any, Unit] =
    updateRequest(request => request.copy(method = update(request.method)))

  /**
   * Creates middleware that will update the path of the request.
   */
  def updatePath(update: Path => Path): HandlerAspect[Any, Unit] =
    updateRequest(request => request.copy(url = request.url.copy(path = update(request.url.path))))

  /**
   * Creates middleware that will update the request.
   */
  def updateRequest(update: Request => Request): HandlerAspect[Any, Unit] =
    HandlerAspect.interceptIncomingHandler {
      Handler.fromFunction[Request] { request =>
        (update(request), ())
      }
    }

  /**
   * Creates middleware that will update the request effectfully.
   */
  def updateRequestZIO[Env](update: Request => ZIO[Env, Response, Request]): HandlerAspect[Env, Unit] =
    HandlerAspect.interceptIncomingHandler {
      Handler.fromFunctionZIO[Request] { request =>
        update(request).map((_, ()))
      }
    }

  /**
   * Creates middleware that will update the response.
   */
  def updateResponse(update: Response => Response): HandlerAspect[Any, Unit] =
    HandlerAspect.interceptOutgoingHandler(Handler.fromFunction(update))

  /**
   * Creates middleware that will update the response effectfully.
   */
  def updateResponseZIO[Env](update: Response => ZIO[Env, Nothing, Response]): HandlerAspect[Env, Unit] =
    HandlerAspect.interceptOutgoingHandler(Handler.fromFunctionZIO[Response](update))

  /**
   * Creates middleware that will update the URL of the request.
   */
  def updateURL(update: URL => URL): HandlerAspect[Any, Unit] =
    updateRequest(request => request.copy(url = update(request.url)))

  /**
   * Applies the middleware only when the header-based condition evaluates to
   * true.
   */
  def whenHeader[Env](condition: Headers => Boolean)(
    middleware: HandlerAspect[Env, Unit],
  ): HandlerAspect[Env, Unit] =
    ifHeaderThenElse(condition)(ifFalse = identity, ifTrue = middleware)

  /**
   * Applies the middleware only if the condition function evaluates to true
   */
  def whenResponse(condition: Response => Boolean)(
    f: Response => Response,
  ): HandlerAspect[Any, Unit] =
    HandlerAspect(
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
  ): HandlerAspect[Env, Unit] =
    HandlerAspect(
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
  def when[Env](condition: Request => Boolean)(
    middleware: HandlerAspect[Env, Unit],
  ): HandlerAspect[Env, Unit] =
    ifRequestThenElse(condition)(ifFalse = identity, ifTrue = middleware)

  /**
   * Applies the middleware only if the condition function effectfully evaluates
   * to true
   */
  def whenZIO[Env](condition: Request => ZIO[Env, Response, Boolean])(
    middleware: HandlerAspect[Env, Unit],
  ): HandlerAspect[Env, Unit] =
    ifRequestThenElseZIO(condition)(ifFalse = identity, ifTrue = middleware)

  private def replaceErrorResponse(request: Request, response: Response): Response = {
    def htmlResponse: Body = {
      val message: String = response.header(Header.Warning).map(_.text).getOrElse("")
      val data            = Template.container(s"${response.status}") {
        div(
          div(
            styles := Seq("text-align" -> "center"),
            div(s"${response.status.code}", styles := Seq("font-size" -> "20em")),
            div(message),
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

  private def formatErrorMessage(response: Response) = {
    val errorMessage: String = response.header(Header.Warning).map(_.text).getOrElse("")
    val status               = response.status.code
    s"${scala.Console.BOLD}${scala.Console.RED}${response.status} ${scala.Console.RESET} - " +
      s"${scala.Console.BOLD}${scala.Console.CYAN}$status ${scala.Console.RESET} - " +
      s"$errorMessage"
  }

  private[http] val defaultBoundaries = MetricKeyType.Histogram.Boundaries.fromChunk(
    Chunk(
      .005, .01, .025, .05, .075, .1, .25, .5, .75, 1, 2.5, 5, 7.5, 10,
    ),
  )
}
