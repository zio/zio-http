package zio.http.middleware

import io.netty.handler.codec.http.HttpHeaderValues
import zio._
import zio.http.URL.encode
import zio.http._
import zio.http.html._
import zio.http.middleware.WebForTotal.{PartialInterceptPatch, PartialInterceptZIOPatch}
import zio.http.model._
import zio.http.model.headers._

import java.io.{IOException, PrintWriter, StringWriter}
import zio.stacktracer.TracingImplicits.disableAutoTrace // scalafix:ok;

/**
 * Middlewares on an HttpApp
 */
private[zio] trait WebForTotal
    extends AuthForTotal
    with RequestLoggingForTotal
    with MetricsForTotal
    with HeaderModifier[HttpMiddlewareForTotal[Any, Nothing]] {
  self =>

  /**
   * Sets cookie in response headers
   */
  final def addCookie(cookie: Cookie[Response]): HttpMiddlewareForTotal[Any, Nothing] =
    self.withSetCookie(cookie)

  final def addCookieZIO[R, E](cookie: ZIO[R, E, Cookie[Response]]): HttpMiddlewareForTotal[R, E] =
    new HttpMiddlewareForTotal[R, E] {
      override def apply[R1 <: R, E1 >: E](
        app: Http.Total[R1, E1, Request, Response],
      )(implicit trace: Trace): Http.Total[R1, E1, Request, Response] =
        Http.fromFunctionZIO { request =>
          for {
            response <- app.toZIO(request)
            patch    <- cookie.map(c => Patch.addHeader(Headers.setCookie(c)))
          } yield patch(response)
        }
    }

  /**
   * Beautify the error response.
   */
  final def beautifyErrors: HttpMiddlewareForTotal[Any, Nothing] =
    Middleware.ForTotal.intercept[Request, Response](identity)((res, req) => Web.updateErrorResponse(res, req))

  /**
   * Add log status, method, url and time taken from req to res
   */
  final def debug: HttpMiddlewareForTotal[Any, IOException] =
    new HttpMiddlewareForTotal[Any, IOException] {
      override def apply[R, E >: IOException](app: Http.Total[R, E, Request, Response])(implicit
        trace: Trace,
      ): Http.Total[R, E, Request, Response] =
        Http.fromFunctionZIO { request =>
          for {
            start    <- Clock.nanoTime
            response <- app.toZIO(request)
            end      <- Clock.nanoTime
            _        <- Console
              .printLine(
                s"${response.status.asJava.code()} ${request.method} ${request.url.encode} ${(end - start) / 1000000}ms",
              )
          } yield response
        }
    }

  /**
   * Removes the trailing slash from the path.
   */
  final def dropTrailingSlash: HttpMiddlewareForTotal[Any, Nothing] =
    Middleware.ForTotal
      .identity[Request, Response]
      .contramap[Request](_.dropTrailingSlash)
      .when(_.url.queryParams.isEmpty)

  /**
   * Logical operator to decide which middleware to select based on the header
   */
  final def ifHeaderThenElse[R, E](
    cond: Headers => Boolean,
  )(left: HttpMiddlewareForTotal[R, E], right: HttpMiddlewareForTotal[R, E]): HttpMiddlewareForTotal[R, E] =
    Middleware.ForTotal.ifThenElse[Request](req => cond(req.headers))(_ => left, _ => right)

  /**
   * Logical operator to decide which middleware to select based on the method.
   */
  final def ifMethodThenElse[R, E](
    cond: Method => Boolean,
  )(left: HttpMiddlewareForTotal[R, E], right: HttpMiddlewareForTotal[R, E]): HttpMiddlewareForTotal[R, E] =
    Middleware.ForTotal.ifThenElse[Request](req => cond(req.method))(_ => left, _ => right)

  /**
   * Logical operator to decide which middleware to select based on the
   * predicate.
   */
  final def ifRequestThenElse[R, E](
    cond: Request => Boolean,
  )(left: HttpMiddlewareForTotal[R, E], right: HttpMiddlewareForTotal[R, E]): HttpMiddlewareForTotal[R, E] =
    Middleware.ForTotal.ifThenElse[Request](cond)(_ => left, _ => right)

  /**
   * Logical operator to decide which middleware to select based on the
   * predicate.
   */
  final def ifRequestThenElseZIO[R, E](
    cond: Request => ZIO[R, E, Boolean],
  )(left: HttpMiddlewareForTotal[R, E], right: HttpMiddlewareForTotal[R, E]): HttpMiddlewareForTotal[R, E] =
    Middleware.ifThenElseZIO[Request](cond)(_ => left, _ => right)

  /**
   * Creates a new middleware using transformation functions
   */
  final def interceptPatch[S](req: Request => S): PartialInterceptPatch[S] = PartialInterceptPatch(req)

  /**
   * Creates a new middleware using effectful transformation functions
   */
  final def interceptZIOPatch[R, E, S](req: Request => ZIO[R, E, S]): PartialInterceptZIOPatch[R, E, S] =
    PartialInterceptZIOPatch(req)

  /**
   * Creates a middleware that produces a Patch for the Response
   */
  final def patch[R, E](f: Response => Patch): HttpMiddlewareForTotal[R, E] =
    Middleware.ForTotal.interceptPatch(_ => ())((res, _) => f(res))

  /**
   * Creates a middleware that produces a Patch for the Response effectfully.
   */
  final def patchZIO[R, E](f: Response => ZIO[R, E, Patch]): HttpMiddlewareForTotal[R, E] =
    Middleware.ForTotal.interceptZIOPatch(_ => ZIO.unit)((res, _) => f(res))

  /**
   * Client redirect temporary or permanent to specified url.
   */
  final def redirect(url: URL, permanent: Boolean): HttpMiddlewareForTotal[Any, Nothing] =
    Middleware.ForTotal.fromHttp(
      Http.response(Response.redirect(encode(url), isPermanent = permanent)),
    )

  /**
   * Permanent redirect if the trailing slash is present in the request URL.
   */
  final def redirectTrailingSlash(permanent: Boolean): HttpMiddlewareForTotal[Any, Nothing] =
    Middleware.ForTotal.ifThenElse[Request](_.url.path.trailingSlash)(
      req => redirect(req.dropTrailingSlash.url, permanent).when(_.url.queryParams.isEmpty),
      _ => Middleware.ForTotal.identity,
    )

  /**
   * Runs the effect after the middleware is applied
   */
  final def runAfter[R, E](effect: ZIO[R, E, Any]): HttpMiddlewareForTotal[R, E] =
    new HttpMiddlewareForTotal[R, E] {
      override def apply[R1 <: R, E1 >: E](app: Http.Total[R1, E1, Request, Response])(implicit
        trace: Trace,
      ): Http.Total[R1, E1, Request, Response] =
        Http.fromFunctionZIO { request =>
          for {
            response <- app.toZIO(request)
            _        <- effect
          } yield response
        }
    }

  /**
   * Runs the effect before the request is passed on to the HttpApp on which the
   * middleware is applied.
   */
  final def runBefore[R, E](effect: ZIO[R, E, Any]): HttpMiddlewareForTotal[R, E] =
    new HttpMiddlewareForTotal[R, E] {
      override def apply[R1 <: R, E1 >: E](app: Http.Total[R1, E1, Request, Response])(implicit
        trace: Trace,
      ): Http.Total[R1, E1, Request, Response] =
        Http.fromFunctionZIO { request =>
          for {
            _        <- effect
            response <- app.toZIO(request)
          } yield response
        }
    }

  /**
   * Creates a new middleware that always sets the response status to the
   * provided value
   */
  final def setStatus(status: Status): HttpMiddlewareForTotal[Any, Nothing] =
    patch(_ => Patch.setStatus(status))

  /**
   * Creates a middleware for signing cookies
   */
  final def signCookies(secret: String): HttpMiddlewareForTotal[Any, Nothing] =
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
  final def timeout(duration: Duration): HttpMiddlewareForTotal[Any, Nothing] =
    new HttpMiddlewareForTotal[Any, Nothing] {
      def apply[R, E](
        app: Http.Total[R, E, Request, Response],
      )(implicit trace: Trace): Http.Total[R, E, Request, Response] =
        Http.fromFunctionZIO { request =>
          app.toZIO(request).timeoutTo(Response.status(Status.RequestTimeout))(identity)(duration)
        }
    }

  /**
   * Updates the provided list of headers to the response
   */
  final def updateHeaders(update: Headers => Headers): HttpMiddlewareForTotal[Any, Nothing] =
    Middleware.ForTotal.updateResponse(_.updateHeaders(update))

  /**
   * Creates a middleware that updates the response produced
   */
  final def updateResponse[R, E](f: Response => Response): HttpMiddlewareForTotal[R, E] =
    Middleware.ForTotal.intercept[Request, Response](_ => ())((res, _) => f(res))

  /**
   * Applies the middleware only when the condition for the headers are true
   */
  final def whenHeader[R, E](
    cond: Headers => Boolean,
    middleware: HttpMiddlewareForTotal[R, E],
  ): HttpMiddlewareForTotal[R, E] =
    middleware.when(req => cond(req.headers))

  /**
   * Applies the middleware only if status matches the condition
   */
  final def whenStatus[R, E](cond: Status => Boolean)(
    middleware: HttpMiddlewareForTotal[R, E],
  ): HttpMiddlewareForTotal[R, E] =
    whenResponse(respon => cond(respon.status))(middleware)

  /**
   * Applies the middleware only if the condition function evaluates to true
   */
  final def whenRequest[R, E](cond: Request => Boolean)(
    middleware: HttpMiddlewareForTotal[R, E],
  ): HttpMiddlewareForTotal[R, E] =
    middleware.when(cond)

  /**
   * Applies the middleware only if the condition function effectfully evaluates
   * to true
   */
  final def whenRequestZIO[R, E](
    cond: Request => ZIO[R, E, Boolean],
  )(middleware: HttpMiddlewareForTotal[R, E]): HttpMiddlewareForTotal[R, E] =
    middleware.whenZIO(cond)

  /**
   * Applies the middleware only if the condition function evaluates to true
   */
  def whenResponse[R, E](
    cond: Response => Boolean,
  )(middleware: HttpMiddlewareForTotal[R, E]): HttpMiddlewareForTotal[R, E] =
    Middleware.ForTotal.identity[Request, Response].flatMap(response => middleware.when(_ => cond(response)))

  /**
   * Applies the middleware only if the condition function effectfully evaluates
   * to true
   */
  def whenResponseZIO[R, E](
    cond: Response => ZIO[R, E, Boolean],
  )(middleware: HttpMiddlewareForTotal[R, E]): HttpMiddlewareForTotal[R, E] =
    Middleware.ForTotal.identity[Request, Response].flatMap(response => middleware.whenZIO(_ => cond(response)))
}

object WebForTotal {

  final case class PartialInterceptPatch[S](req: Request => S) extends AnyVal {
    def apply(res: (Response, S) => Patch): HttpMiddlewareForTotal[Any, Nothing] = {
      Middleware.ForTotal.intercept[Request, Response](req(_))((response, state) => res(response, state)(response))
    }
  }

  final case class PartialInterceptZIOPatch[R, E, S](req: Request => ZIO[R, E, S]) extends AnyVal {
    def apply[R1 <: R, E1 >: E](
      res: (Response, S) => ZIO[R1, E1, Patch],
    ): HttpMiddlewareForTotal[R1, E1] =
      new HttpMiddlewareForTotal[R1, E1] {
        def apply[R2 <: R1, E2 >: E1](
          app: Http.Total[R2, E2, Request, Response],
        )(implicit trace: Trace): Http.Total[R2, E2, Request, Response] =
          Http.fromFunctionZIO { a =>
            for {
              s <- req(a)
              b <- app.toZIO(a)
              c <- res(b, s)
            } yield c(b)
          }
      }
  }
}
