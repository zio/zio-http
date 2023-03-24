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

package zio.http.middleware

import zio.stacktracer.TracingImplicits.disableAutoTrace
import zio.{Console, Duration, Trace, ZIO}

import zio.http._
import zio.http.middleware.RequestHandlerMiddlewares.{InterceptPatch, InterceptPatchZIO}
import zio.http.model._
import zio.http.model.headers.HeaderModifier

private[zio] trait RequestHandlerMiddlewares
    extends RequestLogging
    with Metrics
    with Auth
    with HeaderModifier[RequestHandlerMiddleware[Nothing, Any, Nothing, Any]]
    with HtmlErrorResponses { self =>

  /**
   * Sets cookie in response headers
   */
  final def addCookie(cookie: Cookie.Response): RequestHandlerMiddleware[Nothing, Any, Nothing, Any] =
    withHeader(Header.ResponseCookie(cookie))

  final def addCookieZIO[R, E](cookie: ZIO[R, E, Cookie.Response])(implicit
    trace: Trace,
  ): RequestHandlerMiddleware[Nothing, R, E, Any] =
    updateResponseZIO(response => cookie.map(response.addCookie))

  /**
   * Beautify the error response.
   */
  final def beautifyErrors: RequestHandlerMiddleware[Nothing, Any, Nothing, Any] =
    intercept(replaceErrorResponse)

  /**
   * Add log status, method, url and time taken from req to res
   */
  final def debug: RequestHandlerMiddleware[Nothing, Any, Nothing, Any] =
    new RequestHandlerMiddleware.Simple[Any, Nothing] {
      override def apply[R1 <: Any, Err1 >: Nothing](
        handler: Handler[R1, Err1, Request, Response],
      )(implicit trace: Trace): Handler[R1, Err1, Request, Response] =
        Handler.fromFunctionZIO { request =>
          handler.runZIO(request).timed.flatMap { case (duration, response) =>
            Console
              .printLine(s"${response.status.code} ${request.method} ${request.url.encode} ${duration.toMillis}ms")
              .as(response)
              .orDie
          }
        }
    }

  final def intercept(
    fromRequestAndResponse: (Request, Response) => Response,
  ): RequestHandlerMiddleware[Nothing, Any, Nothing, Any] =
    new RequestHandlerMiddleware.Simple[Any, Nothing] {
      override def apply[R1 <: Any, Err1 >: Nothing](
        handler: Handler[R1, Err1, Request, Response],
      )(implicit trace: Trace): Handler[R1, Err1, Request, Response] =
        Handler.fromFunctionHandler[Request] { request =>
          handler.map(fromRequestAndResponse(request, _))
        }
    }

  /**
   * Logical operator to decide which middleware to select based on the header
   */
  final def ifHeaderThenElse[UpperEnv, LowerErr](
    condition: Headers => Boolean,
  )(
    ifTrue: RequestHandlerMiddleware[Nothing, UpperEnv, LowerErr, Any],
    ifFalse: RequestHandlerMiddleware[Nothing, UpperEnv, LowerErr, Any],
  ): RequestHandlerMiddleware[Nothing, UpperEnv, LowerErr, Any] =
    ifRequestThenElse(request => condition(request.headers))(ifTrue, ifFalse)

  /**
   * Logical operator to decide which middleware to select based on the method.
   */
  final def ifMethodThenElse[UpperEnv, LowerErr](
    condition: Method => Boolean,
  )(
    ifTrue: RequestHandlerMiddleware[Nothing, UpperEnv, LowerErr, Any],
    ifFalse: RequestHandlerMiddleware[Nothing, UpperEnv, LowerErr, Any],
  ): RequestHandlerMiddleware[Nothing, UpperEnv, LowerErr, Any] =
    ifRequestThenElse(request => condition(request.method))(ifTrue, ifFalse)

  /**
   * Logical operator to decide which middleware to select based on the
   * predicate.
   */
  final def ifRequestThenElse[UpperEnv, LowerErr](
    condition: Request => Boolean,
  )(
    ifTrue: RequestHandlerMiddleware[Nothing, UpperEnv, LowerErr, Any],
    ifFalse: RequestHandlerMiddleware[Nothing, UpperEnv, LowerErr, Any],
  ): RequestHandlerMiddleware[Nothing, UpperEnv, LowerErr, Any] =
    new RequestHandlerMiddleware.Simple[UpperEnv, LowerErr] {

      override def apply[R1 <: UpperEnv, Err1 >: LowerErr](
        handler: Handler[R1, Err1, Request, Response],
      )(implicit trace: Trace): Handler[R1, Err1, Request, Response] =
        Handler.fromFunctionHandler[Request] { request =>
          if (condition(request)) ifTrue(handler) else ifFalse(handler)
        }
    }

  final def ifRequestThenElseFunction[UpperEnv, LowerErr](
    condition: Request => Boolean,
  )(
    ifTrue: Request => RequestHandlerMiddleware[Nothing, UpperEnv, LowerErr, Any],
    ifFalse: Request => RequestHandlerMiddleware[Nothing, UpperEnv, LowerErr, Any],
  ): RequestHandlerMiddleware[Nothing, UpperEnv, LowerErr, Any] =
    new RequestHandlerMiddleware.Simple[UpperEnv, LowerErr] {

      override def apply[R1 <: UpperEnv, Err1 >: LowerErr](
        handler: Handler[R1, Err1, Request, Response],
      )(implicit trace: Trace): Handler[R1, Err1, Request, Response] =
        Handler.fromFunctionHandler[Request] { request =>
          if (condition(request)) ifTrue(request)(handler) else ifFalse(request)(handler)
        }
    }

  /**
   * Logical operator to decide which middleware to select based on the
   * predicate.
   */
  final def ifRequestThenElseZIO[R, E](
    condition: Request => ZIO[R, E, Boolean],
  )(
    ifTrue: RequestHandlerMiddleware[Nothing, R, E, Any],
    ifFalse: RequestHandlerMiddleware[Nothing, R, E, Any],
  ): RequestHandlerMiddleware[Nothing, R, E, Any] =
    new RequestHandlerMiddleware.Simple[R, E] {

      override def apply[R1 <: R, Err1 >: E](
        handler: Handler[R1, Err1, Request, Response],
      )(implicit trace: Trace): Handler[R1, Err1, Request, Response] =
        Handler
          .fromFunctionZIO[Request] { request =>
            condition(request).map {
              case true  => ifTrue(handler)
              case false => ifFalse(handler)
            }
          }
          .flatten
    }

  final def ifRequestThenElseFunctionZIO[R, E](
    condition: Request => ZIO[R, E, Boolean],
  )(
    ifTrue: Request => RequestHandlerMiddleware.Simple[R, E],
    ifFalse: Request => RequestHandlerMiddleware.Simple[R, E],
  ): RequestHandlerMiddleware[Nothing, R, E, Any] =
    new RequestHandlerMiddleware.Simple[R, E] {

      override def apply[R1 <: R, Err1 >: E](
        handler: Handler[R1, Err1, Request, Response],
      )(implicit trace: Trace): Handler[R1, Err1, Request, Response] =
        Handler
          .fromFunctionZIO[Request] { request =>
            condition(request).map {
              case true  => ifTrue(request)(handler)
              case false => ifFalse(request)(handler)
            }
          }
          .flatten
    }

  /**
   * Creates a new middleware using transformation functions
   */
  final def interceptPatch[S](fromRequest: Request => S): InterceptPatch[S] = new InterceptPatch[S](fromRequest)

  /**
   * Creates a new middleware using effectful transformation functions
   */
  final def interceptPatchZIO[R, E, S](fromRequest: Request => ZIO[R, E, S]): InterceptPatchZIO[R, E, S] =
    new InterceptPatchZIO[R, E, S](fromRequest)

  /**
   * Creates a middleware that produces a Patch for the Response
   */
  final def patch(f: Response => Patch): RequestHandlerMiddleware[Nothing, Any, Nothing, Any] =
    interceptPatch(_ => ())((response, _) => f(response))

  /**
   * Creates a middleware that produces a Patch for the Response effectfully.
   */
  final def patchZIO[R, E](f: Response => ZIO[R, E, Patch]): RequestHandlerMiddleware[Nothing, R, E, Any] =
    interceptPatchZIO(_ => ZIO.unit)((response, _) => f(response))

  /**
   * Client redirect temporary or permanent to specified url.
   */
  final def redirect(url: URL, isPermanent: Boolean): RequestHandlerMiddleware[Nothing, Any, Nothing, Any] =
    replace(Handler.succeed(Response.redirect(url, isPermanent)))

  /**
   * Permanent redirect if the trailing slash is present in the request URL.
   */
  final def redirectTrailingSlash(
    isPermanent: Boolean,
  )(implicit trace: Trace): RequestHandlerMiddleware[Nothing, Any, Nothing, Any] =
    ifRequestThenElseFunction(request => request.url.path.trailingSlash && request.url.queryParams.isEmpty)(
      ifFalse = _ => RequestHandlerMiddleware.identity,
      ifTrue = request => redirect(request.dropTrailingSlash.url, isPermanent),
    )

  final def replace[R, E](newHandler: RequestHandler[R, E]): RequestHandlerMiddleware[Nothing, R, E, Any] =
    new RequestHandlerMiddleware.Simple[R, E] {
      override def apply[R1 <: R, Err1 >: E](handler: Handler[R1, Err1, Request, Response])(implicit
        trace: Trace,
      ): Handler[R1, Err1, Request, Response] =
        newHandler
    }

  /**
   * Runs the effect after the middleware is applied
   */
  final def runAfter[R, E](effect: ZIO[R, E, Any])(implicit
    trace: Trace,
  ): RequestHandlerMiddleware[Nothing, R, E, Any] =
    updateResponseZIO(response => effect.as(response))

  /**
   * Runs the effect before the request is passed on to the HttpApp on which the
   * middleware is applied.
   */
  final def runBefore[R, E](effect: ZIO[R, E, Any]): RequestHandlerMiddleware[Nothing, R, E, Any] =
    new RequestHandlerMiddleware.Simple[R, E] {

      override def apply[R1 <: R, Err1 >: E](
        handler: Handler[R1, Err1, Request, Response],
      )(implicit trace: Trace): Handler[R1, Err1, Request, Response] =
        handler.contramapZIO(request => effect.as(request))
    }

  /**
   * Creates a new middleware that always sets the response status to the
   * provided value
   */
  final def setStatus(status: Status): RequestHandlerMiddleware[Nothing, Any, Nothing, Any] =
    patch(_ => Patch.setStatus(status))

  /**
   * Creates a middleware for signing cookies
   */
  final def signCookies(secret: String): RequestHandlerMiddleware[Nothing, Any, Nothing, Any] =
    updateHeaders { headers =>
      headers.modify {
        case Header.ResponseCookie(cookie)                                                      =>
          Header.ResponseCookie(cookie.sign(secret))
        case header @ Header.Custom(name, value) if name.toString == Header.ResponseCookie.name =>
          Header.ResponseCookie.parse(value.toString) match {
            case Left(_)               => header
            case Right(responseCookie) => Header.ResponseCookie(responseCookie.value.sign(secret))
          }
        case header: Header                                                                     => header
      }
    }

  /**
   * Times out the application with a 408 status code.
   */
  final def timeout(duration: Duration): RequestHandlerMiddleware[Nothing, Any, Nothing, Any] =
    new RequestHandlerMiddleware.Simple[Any, Nothing] {

      override def apply[R1 <: Any, Err1 >: Nothing](
        handler: Handler[R1, Err1, Request, Response],
      )(implicit trace: Trace): Handler[R1, Err1, Request, Response] =
        Handler.fromFunctionZIO[Request] { request =>
          handler.runZIO(request).timeoutTo(Response.status(Status.RequestTimeout))(identity)(duration)
        }
    }

  /**
   * Updates the provided list of headers to the response
   */
  override final def updateHeaders(update: Headers => Headers): RequestHandlerMiddleware[Nothing, Any, Nothing, Any] =
    updateResponse(_.updateHeaders(update))

  /**
   * Creates a middleware that updates the response produced
   */
  final def updateResponse(f: Response => Response): RequestHandlerMiddleware[Nothing, Any, Nothing, Any] =
    new RequestHandlerMiddleware.Simple[Any, Nothing] {
      override def apply[R1 <: Any, Err1 >: Nothing](
        handler: Handler[R1, Err1, Request, Response],
      )(implicit trace: Trace): Handler[R1, Err1, Request, Response] =
        handler.map(f)
    }

  final def updateResponseZIO[R, E](f: Response => ZIO[R, E, Response]): RequestHandlerMiddleware[Nothing, R, E, Any] =
    new RequestHandlerMiddleware.Simple[R, E] {
      override def apply[R1 <: R, Err1 >: E](
        handler: Handler[R1, Err1, Request, Response],
      )(implicit trace: Trace): Handler[R1, Err1, Request, Response] =
        handler.mapZIO(f)
    }

  /**
   * Applies the middleware only when the condition for the headers are true
   */
  final def whenHeader[R, E](condition: Headers => Boolean)(
    middleware: RequestHandlerMiddleware[Nothing, R, E, Any],
  ): RequestHandlerMiddleware[Nothing, R, E, Any] =
    ifHeaderThenElse(condition)(ifFalse = RequestHandlerMiddleware.identity, ifTrue = middleware)

  /**
   * Applies the middleware only if status matches the condition
   */
  final def whenStatus[R, E](condition: Status => Boolean)(
    middleware: RequestHandlerMiddleware[Nothing, R, E, Any],
  ): RequestHandlerMiddleware[Nothing, R, E, Any] =
    whenResponse(response => condition(response.status))(middleware)

  /**
   * Applies the middleware only if the condition function evaluates to true
   */
  final def whenResponse[R, E](condition: Response => Boolean)(
    middleware: RequestHandlerMiddleware[Nothing, R, E, Any],
  ): RequestHandlerMiddleware[Nothing, R, E, Any] =
    new RequestHandlerMiddleware.Simple[R, E] {
      override def apply[R1 <: R, Err1 >: E](
        handler: Handler[R1, Err1, Request, Response],
      )(implicit trace: Trace): Handler[R1, Err1, Request, Response] =
        handler.flatMap { response =>
          if (condition(response)) middleware(handler)
          else Handler.succeed(response)
        }
    }

  /**
   * Applies the middleware only if the condition function effectfully evaluates
   * to true
   */
  final def whenResponseZIO[R, E](condition: Response => ZIO[R, E, Boolean])(
    middleware: RequestHandlerMiddleware[Nothing, R, E, Any],
  ): RequestHandlerMiddleware[Nothing, R, E, Any] =
    new RequestHandlerMiddleware.Simple[R, E] {
      override def apply[R1 <: R, Err1 >: E](
        handler: Handler[R1, Err1, Request, Response],
      )(implicit trace: Trace): Handler[R1, Err1, Request, Response] =
        handler.flatMap { response =>
          Handler.fromZIO {
            condition(response).map { result =>
              if (result) middleware(handler)
              else Handler.succeed(response)
            }
          }.flatten
        }
    }

  /**
   * Applies the middleware only if the condition function evaluates to true
   */
  final def whenRequest[R, E](condition: Request => Boolean)(
    middleware: RequestHandlerMiddleware[Nothing, R, E, Any],
  ): RequestHandlerMiddleware[Nothing, R, E, Any] =
    ifRequestThenElse(condition)(ifFalse = RequestHandlerMiddleware.identity, ifTrue = middleware)

  final def whenRequestZIO[R, E](condition: Request => ZIO[R, E, Boolean])(
    middleware: RequestHandlerMiddleware[Nothing, R, E, Any],
  ): RequestHandlerMiddleware[Nothing, R, E, Any] =
    ifRequestThenElseZIO(condition)(ifFalse = RequestHandlerMiddleware.identity, ifTrue = middleware)
}

object RequestHandlerMiddlewares extends RequestHandlerMiddlewares {

  final class InterceptPatch[S](val fromRequest: Request => S) extends AnyVal {
    def apply(result: (Response, S) => Patch): RequestHandlerMiddleware[Nothing, Any, Nothing, Any] =
      new RequestHandlerMiddleware.Simple[Any, Nothing] {
        override def apply[R1 <: Any, Err1 >: Nothing](
          handler: Handler[R1, Err1, Request, Response],
        )(implicit trace: Trace): Handler[R1, Err1, Request, Response] =
          Handler.fromFunctionHandler { (request: Request) =>
            val s = fromRequest(request)
            handler.map { response =>
              result(response, s)(response)
            }
          }
      }
  }

  final class InterceptPatchZIO[R, E, S](val fromRequest: Request => ZIO[R, E, S]) extends AnyVal {
    def apply[R1 <: R, E1 >: E](
      result: (Response, S) => ZIO[R1, E1, Patch],
    ): RequestHandlerMiddleware[Nothing, R1, E1, Any] =
      new RequestHandlerMiddleware.Simple[R1, E1] {
        override def apply[R2 <: R1, Err2 >: E1](
          handler: Handler[R2, Err2, Request, Response],
        )(implicit trace: Trace): Handler[R2, Err2, Request, Response] =
          Handler.fromFunctionZIO { (request: Request) =>
            for {
              s        <- fromRequest(request)
              response <- handler.runZIO(request)
              patch    <- result(response, s)
            } yield patch(response)
          }
      }
  }
}
