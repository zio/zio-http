package zhttp.http.middleware

import zhttp.http._
import zhttp.http.headers.HeaderModifier
import zhttp.http.middleware.Web.{PartialResponseMake, PartialResponseMakeZIO}
import zio.clock.Clock
import zio.console.Console
import zio.duration.Duration
import zio.{UIO, ZIO, clock, console}

import java.io.IOException

/**
 * Middlewares on an HttpApp
 */
private[zhttp] trait Web extends Cors with Csrf with Auth with HeaderModifier[HttpMiddleware[Any, Nothing]] {
  self =>

  /**
   * Logical operator to decide which middleware to select based on the predicate.
   */
  def ifRequestThenElse[R, E](
    cond: MiddlewareRequest => Boolean,
  )(left: HttpMiddleware[R, E], right: HttpMiddleware[R, E]): HttpMiddleware[R, E] =
    Middleware.ifThenElse[Request](req => cond(MiddlewareRequest(req)))(_ => left, _ => right)

  /**
   * Logical operator to decide which middleware to select based on the predicate.
   */
  def ifRequestThenElseZIO[R, E](
    cond: MiddlewareRequest => ZIO[R, E, Boolean],
  )(left: HttpMiddleware[R, E], right: HttpMiddleware[R, E]): HttpMiddleware[R, E] =
    Middleware.ifThenElseZIO[Request](req => cond(MiddlewareRequest(req)))(_ => left, _ => right)

  /**
   * Applies the middleware only if the condition function evaluates to true
   */
  def whenRequest[R, E](cond: MiddlewareRequest => Boolean)(middleware: HttpMiddleware[R, E]): HttpMiddleware[R, E] =
    middleware.when[Request](req => cond(MiddlewareRequest(req)))

  /**
   * Applies the middleware only if the condition function effectfully evaluates to true
   */
  def whenRequestZIO[R, E](
    cond: MiddlewareRequest => ZIO[R, E, Boolean],
  )(middleware: HttpMiddleware[R, E]): HttpMiddleware[R, E] =
    Middleware.ifThenElseZIO[Request](req => cond(MiddlewareRequest(req)))(
      _ => middleware,
      _ => Middleware.identity,
    )

  /**
   * Logical operator to decide which middleware to select based on the header
   */
  def ifHeaderThenElse[R, E](
    cond: Headers => Boolean,
  )(left: HttpMiddleware[R, E], right: HttpMiddleware[R, E]): HttpMiddleware[R, E] =
    Middleware.ifThenElse[Request](req => cond(req.getHeaders))(_ => left, _ => right)

  /**
   * Applies the middleware only when the condition for the headers are true
   */
  def whenHeader[R, E](cond: Headers => Boolean, middleware: HttpMiddleware[R, E]): HttpMiddleware[R, E] =
    middleware.when[Request](req => cond(req.getHeaders))

  /**
   * Sets cookie in response headers
   */
  def addCookie(cookie: Cookie): HttpMiddleware[Any, Nothing] =
    self.withSetCookie(cookie)

  /**
   * Updates the provided list of headers to the response
   */
  override def updateHeaders(update: Headers => Headers): HttpMiddleware[Any, Nothing] =
    Web.updateHeaders(update)

  def addCookieZIO[R, E](cookie: ZIO[R, E, Cookie]): HttpMiddleware[R, E] =
    patchZIO((_, _) => cookie.mapBoth(Option(_), c => Patch.addHeader(Headers.setCookie(c))))

  /**
   * Add log status, method, url and time taken from req to res
   */
  def debug: HttpMiddleware[Console with Clock, IOException] =
    makeResponseZIO(req => zio.clock.nanoTime.map(start => (req.method, req.url, start))) {
      case (status, _, (method, url, start)) =>
        for {
          end <- clock.nanoTime
          _   <- console
            .putStrLn(s"${status.asJava.code()} ${method} ${url.asString} ${(end - start) / 1000000}ms")
            .mapError(Option(_))
        } yield Patch.empty
    }

  /**
   * Creates a new middleware using transformation functions
   */
  def makeResponse[S](req: MiddlewareRequest => S): PartialResponseMake[S] = PartialResponseMake(req)

  /**
   * Creates a new middleware using effectful transformation functions
   */
  def makeResponseZIO[R, E, S](req: MiddlewareRequest => ZIO[R, Option[E], S]): PartialResponseMakeZIO[R, E, S] =
    PartialResponseMakeZIO(req)

  /**
   * Creates a middleware that produces a Patch for the Response
   */
  def patch[R, E](f: (Status, Headers) => Patch): HttpMiddleware[R, E] =
    makeResponse(_ => ())((status, headers, _) => f(status, headers))

  /**
   * Creates a middleware that produces a Patch for the Response effectfully.
   */
  def patchZIO[R, E](f: (Status, Headers) => ZIO[R, Option[E], Patch]): HttpMiddleware[R, E] =
    makeResponseZIO(_ => ZIO.unit)((status, headers, _) => f(status, headers))

  /**
   * Runs the effect before the request is passed on to the HttpApp on which the middleware is applied.
   */
  def runBefore[R, E](effect: ZIO[R, E, Any]): HttpMiddleware[R, E] =
    makeResponseZIO(_ => effect.mapError(Option(_)).unit)((_, _, _) => UIO(Patch.empty))

  /**
   * Creates a new middleware that always sets the response status to the provided value
   */
  def setStatus(status: Status): HttpMiddleware[Any, Nothing] = patch((_, _) => Patch.setStatus(status))

  /**
   * Creates a middleware for signing cookies
   */
  def signCookies(secret: String): HttpMiddleware[Any, Nothing] =
    updateHeaders {
      case h if h.getHeader(HeaderNames.setCookie).isDefined =>
        Headers(
          HeaderNames.setCookie,
          Cookie.decodeResponseCookie(h.getHeader(HeaderNames.setCookie).get._2.toString).get.sign(secret).encode,
        )
      case h                                                 => h
    }

  /**
   * Creates a new constants middleware that always executes the app provided, independent of where the middleware is
   * applied
   */
  def fromApp[R, E](app: HttpApp[R, E]): HttpMiddleware[R, E] = Middleware.fromHttp(app)

  /**
   * Times out the application with a 408 status code.
   */
  def timeout(duration: Duration): HttpMiddleware[Clock, Nothing] =
    Middleware.identity.race(Middleware.fromApp(Http.status(Status.REQUEST_TIMEOUT).delayAfter(duration)))

  /**
   * Creates a new middleware using a function from request parameters to a HttpMiddleware
   */
  def fromMiddlewareFunction[R, E](f: MiddlewareRequest => HttpMiddleware[R, E]): HttpMiddleware[R, E] =
    Middleware.make(req => f(MiddlewareRequest(req)))

  /**
   * Creates a new middleware using a function from request parameters to a ZIO of HttpMiddleware
   */
  def fromMiddlewareFunctionZIO[R, E](f: MiddlewareRequest => ZIO[R, E, HttpMiddleware[R, E]]): HttpMiddleware[R, E] =
    Middleware.makeZIO(req => f(MiddlewareRequest(req)))
}

object Web extends HeaderModifier[HttpMiddleware[Any, Nothing]] {

  final case class PartialResponseMake[S](req: MiddlewareRequest => S) extends AnyVal {
    def apply(res: (Status, Headers, S) => Patch): HttpMiddleware[Any, Nothing] = {
      Middleware.intercept[Request, Response](
        incoming = request => req(MiddlewareRequest(request)),
      )(
        outgoing = (response, state) => res(response.status, response.getHeaders, state)(response),
      )
    }
  }

  final case class PartialResponseMakeZIO[R, E, S](req: MiddlewareRequest => ZIO[R, Option[E], S]) extends AnyVal {
    def apply[R1 <: R, E1 >: E](res: (Status, Headers, S) => ZIO[R1, Option[E1], Patch]): HttpMiddleware[R1, E1] =
      Middleware
        .interceptZIO[Request, Response]
        .apply[R1, E1, S, Response](
          incoming = request => req(MiddlewareRequest(request)),
        )(
          outgoing = (response, state) => res(response.status, response.getHeaders, state).map(patch => patch(response)),
        )
  }

  /**
   * Updates the current Headers with new one, using the provided update function passed.
   */
  override def updateHeaders(update: Headers => Headers): HttpMiddleware[Any, Nothing] =
    Middleware.patch((_, _) => Patch.updateHeaders(update))
}
