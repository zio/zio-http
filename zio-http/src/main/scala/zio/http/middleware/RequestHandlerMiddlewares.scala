package zio.http.middleware

import zio.http._
import zio.http.middleware.RequestHandlerMiddlewares.{InterceptPatch, InterceptPatchZIO}
import zio.http.model.headers.HeaderModifier
import zio.http.model.{Cookie, HeaderNames, Headers, Method, Status}
import zio.{Console, Duration, Trace, ZIO}

import java.io.IOException

import zio.stacktracer.TracingImplicits.disableAutoTrace // scalafix:ok;

private[zio] trait RequestHandlerMiddlewares
    extends RequestLogging
    with Metrics
    with Auth
    with HeaderModifier[RequestHandlerMiddleware[Any, Nothing]]
    with HtmlErrorResponses { self =>

  /**
   * Sets cookie in response headers
   */
  final def addCookie(cookie: Cookie[Response]): RequestHandlerMiddleware[Any, Nothing] =
    withSetCookie(cookie)

  final def addCookieZIO[R, E](cookie: ZIO[R, E, Cookie[Response]])(implicit
    trace: Trace,
  ): RequestHandlerMiddleware[R, E] =
    updateResponseZIO(response => cookie.map(response.addCookie))

  /**
   * Beautify the error response.
   */
  final def beautifyErrors: RequestHandlerMiddleware[Any, Nothing] =
    intercept(replaceErrorResponse)

  /**
   * Add log status, method, url and time taken from req to res
   */
  final def debug: RequestHandlerMiddleware[Any, Nothing] =
    new RequestHandlerMiddleware[Any, Nothing] {
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

  final def intercept(fromRequestAndResponse: (Request, Response) => Response): RequestHandlerMiddleware[Any, Nothing] =
    new RequestHandlerMiddleware[Any, Nothing] {
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
  final def ifHeaderThenElse[R, E](
    condition: Headers => Boolean,
  )(ifTrue: RequestHandlerMiddleware[R, E], ifFalse: RequestHandlerMiddleware[R, E]): RequestHandlerMiddleware[R, E] =
    ifRequestThenElse(request => condition(request.headers))(ifTrue, ifFalse)

  /**
   * Logical operator to decide which middleware to select based on the method.
   */
  final def ifMethodThenElse[R, E](
    condition: Method => Boolean,
  )(ifTrue: RequestHandlerMiddleware[R, E], ifFalse: RequestHandlerMiddleware[R, E]): RequestHandlerMiddleware[R, E] =
    ifRequestThenElse(request => condition(request.method))(ifTrue, ifFalse)

  /**
   * Logical operator to decide which middleware to select based on the
   * predicate.
   */
  final def ifRequestThenElse[R, E](
    condition: Request => Boolean,
  )(ifTrue: RequestHandlerMiddleware[R, E], ifFalse: RequestHandlerMiddleware[R, E]): RequestHandlerMiddleware[R, E] =
    new RequestHandlerMiddleware[R, E] {
      override def apply[R1 <: R, Err1 >: E](
        handler: Handler[R1, Err1, Request, Response],
      )(implicit trace: Trace): Handler[R1, Err1, Request, Response] =
        Handler.fromFunctionHandler[Request] { request =>
          if (condition(request)) ifTrue(handler) else ifFalse(handler)
        }
    }

  final def ifRequestThenElseFunction[R, E](
    condition: Request => Boolean,
  )(
    ifTrue: Request => RequestHandlerMiddleware[R, E],
    ifFalse: Request => RequestHandlerMiddleware[R, E],
  ): RequestHandlerMiddleware[R, E] =
    new RequestHandlerMiddleware[R, E] {
      override def apply[R1 <: R, Err1 >: E](
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
  )(ifTrue: RequestHandlerMiddleware[R, E], ifFalse: RequestHandlerMiddleware[R, E]): RequestHandlerMiddleware[R, E] =
    new RequestHandlerMiddleware[R, E] {
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
    ifTrue: Request => RequestHandlerMiddleware[R, E],
    ifFalse: Request => RequestHandlerMiddleware[R, E],
  ): RequestHandlerMiddleware[R, E] =
    new RequestHandlerMiddleware[R, E] {
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
  final def patch(f: Response => Patch): RequestHandlerMiddleware[Any, Nothing] =
    interceptPatch(_ => ())((response, _) => f(response))

  /**
   * Creates a middleware that produces a Patch for the Response effectfully.
   */
  final def patchZIO[R, E](f: Response => ZIO[R, E, Patch]): RequestHandlerMiddleware[R, E] =
    interceptPatchZIO(_ => ZIO.unit)((response, _) => f(response))

  /**
   * Client redirect temporary or permanent to specified url.
   */
  final def redirect(url: URL, isPermanent: Boolean): RequestHandlerMiddleware[Any, Nothing] =
    replace(Handler.succeed(Response.redirect(url.encode, isPermanent)))

  /**
   * Permanent redirect if the trailing slash is present in the request URL.
   */
  final def redirectTrailingSlash(isPermanent: Boolean)(implicit trace: Trace): RequestHandlerMiddleware[Any, Nothing] =
    ifRequestThenElseFunction(request => request.url.path.trailingSlash && request.url.queryParams.isEmpty)(
      ifFalse = _ => RequestHandlerMiddleware.identity,
      ifTrue = request => redirect(request.dropTrailingSlash.url, isPermanent),
    )

  final def replace[R, E](newHandler: RequestHandler[R, E]): RequestHandlerMiddleware[R, E] =
    new RequestHandlerMiddleware[R, E] {
      override def apply[R1 <: R, Err1 >: E](handler: Handler[R1, Err1, Request, Response])(implicit
        trace: Trace,
      ): Handler[R1, Err1, Request, Response] =
        newHandler
    }

  /**
   * Runs the effect after the middleware is applied
   */
  final def runAfter[R, E](effect: ZIO[R, E, Any])(implicit trace: Trace): RequestHandlerMiddleware[R, E] =
    updateResponseZIO(response => effect.as(response))

  /**
   * Runs the effect before the request is passed on to the HttpApp on which the
   * middleware is applied.
   */
  final def runBefore[R, E](effect: ZIO[R, E, Any]): RequestHandlerMiddleware[R, E] =
    new RequestHandlerMiddleware[R, E] {
      override def apply[R1 <: R, Err1 >: E](
        handler: Handler[R1, Err1, Request, Response],
      )(implicit trace: Trace): Handler[R1, Err1, Request, Response] =
        handler.contramapZIO(request => effect.as(request))
    }

  /**
   * Creates a new middleware that always sets the response status to the
   * provided value
   */
  final def setStatus(status: Status): RequestHandlerMiddleware[Any, Nothing] =
    patch(_ => Patch.setStatus(status))

  /**
   * Creates a middleware for signing cookies
   */
  final def signCookies(secret: String): RequestHandlerMiddleware[Any, Nothing] =
    updateHeaders {
      case h if h.header(HeaderNames.setCookie).isDefined =>
        Cookie
          .decode[Response](h.header(HeaderNames.setCookie).get._2.toString)
          .map(_.sign(secret))
          .map { cookie => Headers.setCookie(cookie) }
          .getOrElse(h)

      case h => h
    }

  /**
   * Times out the application with a 408 status code.
   */
  final def timeout(duration: Duration): RequestHandlerMiddleware[Any, Nothing] =
    new RequestHandlerMiddleware[Any, Nothing] {
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
  override final def updateHeaders(update: Headers => Headers): RequestHandlerMiddleware[Any, Nothing] =
    updateResponse(_.updateHeaders(update))

  /**
   * Creates a middleware that updates the response produced
   */
  final def updateResponse(f: Response => Response): RequestHandlerMiddleware[Any, Nothing] =
    new RequestHandlerMiddleware[Any, Nothing] {
      override def apply[R1 <: Any, Err1 >: Nothing](
        handler: Handler[R1, Err1, Request, Response],
      )(implicit trace: Trace): Handler[R1, Err1, Request, Response] =
        handler.map(f)
    }

  final def updateResponseZIO[R, E](f: Response => ZIO[R, E, Response]): RequestHandlerMiddleware[R, E] =
    new RequestHandlerMiddleware[R, E] {
      override def apply[R1 <: R, Err1 >: E](
        handler: Handler[R1, Err1, Request, Response],
      )(implicit trace: Trace): Handler[R1, Err1, Request, Response] =
        handler.mapZIO(f)
    }

  /**
   * Applies the middleware only when the condition for the headers are true
   */
  final def whenHeader[R, E](condition: Headers => Boolean)(
    middleware: RequestHandlerMiddleware[R, E],
  ): RequestHandlerMiddleware[R, E] =
    ifHeaderThenElse(condition)(ifFalse = RequestHandlerMiddleware.identity, ifTrue = middleware)

  /**
   * Applies the middleware only if status matches the condition
   */
  final def whenStatus[R, E](condition: Status => Boolean)(
    middleware: RequestHandlerMiddleware[R, E],
  ): RequestHandlerMiddleware[R, E] =
    whenResponse(response => condition(response.status))(middleware)

  /**
   * Applies the middleware only if the condition function evaluates to true
   */
  final def whenResponse[R, E](condition: Response => Boolean)(
    middleware: RequestHandlerMiddleware[R, E],
  ): RequestHandlerMiddleware[R, E] =
    new RequestHandlerMiddleware[R, E] {
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
    middleware: RequestHandlerMiddleware[R, E],
  ): RequestHandlerMiddleware[R, E] =
    new RequestHandlerMiddleware[R, E] {
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
    middleware: RequestHandlerMiddleware[R, E],
  ): RequestHandlerMiddleware[R, E] =
    ifRequestThenElse(condition)(ifFalse = RequestHandlerMiddleware.identity, ifTrue = middleware)

  final def whenRequestZIO[R, E](condition: Request => ZIO[R, E, Boolean])(
    middleware: RequestHandlerMiddleware[R, E],
  ): RequestHandlerMiddleware[R, E] =
    ifRequestThenElseZIO(condition)(ifFalse = RequestHandlerMiddleware.identity, ifTrue = middleware)
}

object RequestHandlerMiddlewares extends RequestHandlerMiddlewares {

  final class InterceptPatch[S](val fromRequest: Request => S) extends AnyVal {
    def apply(result: (Response, S) => Patch): RequestHandlerMiddleware[Any, Nothing] =
      new RequestHandlerMiddleware[Any, Nothing] {
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
    def apply[R1 <: R, E1 >: E](result: (Response, S) => ZIO[R1, E1, Patch]): RequestHandlerMiddleware[R1, E1] =
      new RequestHandlerMiddleware[R1, E1] {
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
