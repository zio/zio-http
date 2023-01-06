package zio.http.middleware

import zio.{Duration, Trace, ZIO}
import zio.http.middleware.RequestHandlerMiddlewares.{InterceptPatch, InterceptPatchZIO}
import zio.http.model.{Cookie, HeaderNames, Headers, Method, Status}
import zio.http.model.headers.HeaderModifier
import zio.http.{Handler, HandlerAspect, Patch, Request, RequestHandler, RequestHandlerMiddleware, Response, URL}

private[zio] trait RequestHandlerMiddlewares
    extends RequestLogging
    with Metrics
    with Auth
    with HeaderModifier[RequestHandlerMiddleware[Any, Nothing]]
    with HtmlErrorResponses { self =>

  final def addCookie(cookie: Cookie[Response]): RequestHandlerMiddleware[Any, Nothing] =
    withSetCookie(cookie)

  final def addCookieZIO[R, E](cookie: ZIO[R, E, Cookie[Response]]): RequestHandlerMiddleware[R, E] =
    updateResponseZIO(response => cookie.map(response.addCookie))

  final def beautifyErrors: RequestHandlerMiddleware[Any, Nothing] =
    intercept(replaceErrorResponse)

  final def debug: RequestHandlerMiddleware[Any, Nothing] =
    new RequestHandlerMiddleware[Any, Nothing] {
      override def apply[R1 <: Any, Err1 >: Nothing](
        handler: Handler[R1, Err1, Request, Response],
      )(implicit trace: Trace): Handler[R1, Err1, Request, Response] =
        Handler.fromFunctionZIO { request =>
          handler.toZIO(request).timed.flatMap { case (duration, response) =>
            ZIO
              .debug(s"${response.status.code} ${request.method} ${request.url.encode} ${duration.toMillis}ms")
              .as(response)
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

  final def ifHeaderThenElse[R, E](
    condition: Headers => Boolean,
  )(ifFalse: RequestHandlerMiddleware[R, E], ifTrue: RequestHandlerMiddleware[R, E]): RequestHandlerMiddleware[R, E] =
    ifRequestThenElse(request => condition(request.headers))(ifFalse, ifTrue)

  final def ifMethodThenElse[R, E](
    condition: Method => Boolean,
  )(ifFalse: RequestHandlerMiddleware[R, E], ifTrue: RequestHandlerMiddleware[R, E]): RequestHandlerMiddleware[R, E] =
    ifRequestThenElse(request => condition(request.method))(ifFalse, ifTrue)

  final def ifRequestThenElse[R, E](
    condition: Request => Boolean,
  )(ifFalse: RequestHandlerMiddleware[R, E], ifTrue: RequestHandlerMiddleware[R, E]): RequestHandlerMiddleware[R, E] =
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
    ifFalse: Request => RequestHandlerMiddleware[R, E],
    ifTrue: Request => RequestHandlerMiddleware[R, E],
  ): RequestHandlerMiddleware[R, E] =
    new RequestHandlerMiddleware[R, E] {
      override def apply[R1 <: R, Err1 >: E](
        handler: Handler[R1, Err1, Request, Response],
      )(implicit trace: Trace): Handler[R1, Err1, Request, Response] =
        Handler.fromFunctionHandler[Request] { request =>
          if (condition(request)) ifTrue(request)(handler) else ifFalse(request)(handler)
        }
    }

  final def ifRequestThenElseZIO[R, E](
    condition: Request => ZIO[R, E, Boolean],
  )(ifFalse: RequestHandlerMiddleware[R, E], ifTrue: RequestHandlerMiddleware[R, E]): RequestHandlerMiddleware[R, E] =
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
    ifFalse: Request => RequestHandlerMiddleware[R, E],
    ifTrue: Request => RequestHandlerMiddleware[R, E],
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

  final def interceptPatch[S](fromRequest: Request => S): InterceptPatch[S] = new InterceptPatch[S](fromRequest)

  final def interceptPatchZIO[R, E, S](fromRequest: Request => ZIO[R, E, S]): InterceptPatchZIO[R, E, S] =
    new InterceptPatchZIO[R, E, S](fromRequest)

  final def patch(f: Response => Patch): RequestHandlerMiddleware[Any, Nothing] =
    interceptPatch(_ => ())((response, _) => f(response))

  final def patchZIO[R, E](f: Response => ZIO[R, E, Patch]): RequestHandlerMiddleware[R, E] =
    interceptPatchZIO(_ => ZIO.unit)((response, _) => f(response))

  final def redirect(url: URL, isPermanent: Boolean): RequestHandlerMiddleware[Any, Nothing] =
    replace(Handler.succeed(Response.redirect(url.encode, isPermanent)))

  final def redirectTrailingSlash(isPermanent: Boolean): RequestHandlerMiddleware[Any, Nothing] =
    ifRequestThenElseFunction(request => request.url.path.trailingSlash && request.url.queryParams.isEmpty)(
      ifFalse = _ => HandlerAspect.identity,
      ifTrue = request => redirect(request.dropTrailingSlash.url, isPermanent),
    )

  final def replace[R, E](newHandler: RequestHandler[R, E]): RequestHandlerMiddleware[R, E] =
    new RequestHandlerMiddleware[R, E] {
      override def apply[R1 <: R, Err1 >: E](handler: Handler[R1, Err1, Request, Response])(implicit
        trace: Trace,
      ): Handler[R1, Err1, Request, Response] =
        newHandler
    }

  final def runAfter[R, E](effect: ZIO[R, E, Any]): RequestHandlerMiddleware[R, E] =
    updateResponseZIO(response => effect.as(response))

  final def runBefore[R, E](effect: ZIO[R, E, Any]): RequestHandlerMiddleware[R, E] =
    new RequestHandlerMiddleware[R, E] {
      override def apply[R1 <: R, Err1 >: E](
        handler: Handler[R1, Err1, Request, Response],
      )(implicit trace: Trace): Handler[R1, Err1, Request, Response] =
        handler.contramapZIO(request => effect.as(request))
    }

  final def setStatus(status: Status): RequestHandlerMiddleware[Any, Nothing] =
    patch(_ => Patch.setStatus(status))

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

  final def timeout(duration: Duration): RequestHandlerMiddleware[Any, Nothing] =
    new RequestHandlerMiddleware[Any, Nothing] {
      override def apply[R1 <: Any, Err1 >: Nothing](
        handler: Handler[R1, Err1, Request, Response],
      )(implicit trace: Trace): Handler[R1, Err1, Request, Response] =
        Handler.fromFunctionZIO[Request] { request =>
          handler.toZIO(request).timeoutTo(Response.status(Status.RequestTimeout))(identity)(duration)
        }
    }

  override final def updateHeaders(update: Headers => Headers): RequestHandlerMiddleware[Any, Nothing] =
    updateResponse(_.updateHeaders(update))

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

  final def whenHeader[R, E](condition: Headers => Boolean)(
    middleware: RequestHandlerMiddleware[R, E],
  ): RequestHandlerMiddleware[R, E] =
    ifHeaderThenElse(condition)(ifFalse = HandlerAspect.identity, ifTrue = middleware)

  final def whenStatus[R, E](condition: Status => Boolean)(
    middleware: RequestHandlerMiddleware[R, E],
  ): RequestHandlerMiddleware[R, E] =
    whenResponse(response => condition(response.status))(middleware)

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

  final def whenRequest[R, E](condition: Request => Boolean)(
    middleware: RequestHandlerMiddleware[R, E],
  ): RequestHandlerMiddleware[R, E] =
    ifRequestThenElse(condition)(ifFalse = HandlerAspect.identity, ifTrue = middleware)

  final def whenRequestZIO[R, E](condition: Request => ZIO[R, E, Boolean])(
    middleware: RequestHandlerMiddleware[R, E],
  ): RequestHandlerMiddleware[R, E] =
    ifRequestThenElseZIO(condition)(ifFalse = HandlerAspect.identity, ifTrue = middleware)
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
              response <- handler.toZIO(request)
              patch    <- result(response, s)
            } yield patch(response)
          }
      }
  }
}
