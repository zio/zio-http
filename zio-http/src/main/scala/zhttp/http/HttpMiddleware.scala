package zhttp.http

import io.netty.handler.codec.http.HttpHeaderNames
import zhttp.http.Headers.BasicSchemeName
import zio.clock.Clock
import zio.console.Console
import zio.{UIO, ZIO, clock, console}

import java.io.IOException
import java.util.UUID

/**
 * Middlewares for Http.
 */

object HttpMiddleware {

  /**
   * Sets cookie in response headers
   */
  def addCookie(cookie: Cookie): HttpMiddleware[Any, Nothing] =
    HttpMiddleware.addHeader(Headers.setCookie(cookie))

  /**
   * Adds the provided header and value to the response
   */
  def addHeader(name: String, value: String): HttpMiddleware[Any, Nothing] =
    patch((_, _) => Patch.addHeader(name, value))

  /**
   * Adds the provided header to the response
   */
  def addHeader(header: Headers): HttpMiddleware[Any, Nothing] =
    patch((_, _) => Patch.addHeader(header))

  /**
   * Adds the provided list of headers to the response
   */
  def addHeaders(headers: Headers): HttpMiddleware[Any, Nothing] =
    patch((_, _) => Patch.addHeader(headers))

  /**
   * Creates an authentication middleware that only allows authenticated requests to be passed on to the app.
   */
  def auth(verify: Headers => Boolean, responseHeaders: Headers = Headers.empty): HttpMiddleware[Any, Nothing] =
    ifThenElse((_, _, h) => verify(h))(
      Middleware.identity,
      Middleware.fromHttp(Http.status(Status.FORBIDDEN).addHeaders(responseHeaders)),
    )

  /**
   * Creates a middleware for basic authentication
   */
  def basicAuth(f: Header => Boolean): HttpMiddleware[Any, Nothing] =
    auth(
      _.getBasicAuthorizationCredentials match {
        case Some(header) => f(header)
        case None         => false
      },
      Headers(HttpHeaderNames.WWW_AUTHENTICATE, BasicSchemeName),
    )

  /**
   * Creates a middleware for basic authentication that checks if the credentials are same as the ones given
   */
  def basicAuth(u: String, p: String): HttpMiddleware[Any, Nothing] =
    basicAuth { case (user, password) => (user == u) && (password == p) }

  def addCookieM[R, E](cookie: ZIO[R, E, Cookie]): HttpMiddleware[R, E] =
    patchZIO((_, _) => cookie.mapBoth(Option(_), c => Patch.addHeader(Headers.setCookie(c))))

  /**
   * CSRF middlewares : To prevent Cross-site request forgery attacks. This middleware is modeled after the double
   * submit cookie pattern.
   * @see
   *   [[HttpMiddleware#csrfGenerate]] - Sets cookie with CSRF token
   * @see
   *   [[HttpMiddleware#csrfValidate]] - Validate token value in request headers against value in cookies
   * @see
   *   https://cheatsheetseries.owasp.org/cheatsheets/Cross-Site_Request_Forgery_Prevention_Cheat_Sheet.html#double-submit-cookie
   */

  def csrfGenerate[R, E](
    tokenName: String = "x-csrf-token",
    tokenGen: ZIO[R, Nothing, String] = UIO(UUID.randomUUID.toString),
  ): HttpMiddleware[R, E] =
    addCookieM(tokenGen.map(Cookie(tokenName, _)))

  def csrfValidate(tokenName: String = "x-csrf-token"): HttpMiddleware[Any, Nothing] = {
    whenHeader(
      headers => {
        (headers.getHeaderValue(tokenName), headers.getCookieValue(tokenName)) match {
          case (Some(headerValue), Some(cookieValue)) => headerValue != cookieValue
          case _                                      => true
        }
      },
      Middleware.fromHttp(Http.status(Status.FORBIDDEN)),
    )
  }

  /**
   * Add log status, method, url and time taken from req to res
   */
  def debug: HttpMiddleware[Console with Clock, IOException] =
    HttpMiddleware.makeZIO((method, url, _) => zio.clock.nanoTime.map(start => (method, url, start))) {
      case (status, _, (method, url, start)) =>
        for {
          end <- clock.nanoTime
          _   <- console
            .putStrLn(s"${status.asJava.code()} ${method} ${url.asString} ${(end - start) / 1000000}ms")
            .mapError(Option(_))
        } yield Patch.empty
    }

  /**
   * Logical operator to decide which middleware to select based on the header
   */
  def ifHeader[R, E](
    cond: Headers => Boolean,
  )(left: HttpMiddleware[R, E], right: HttpMiddleware[R, E]): HttpMiddleware[R, E] =
    ifThenElse((_, _, headers) => cond(headers))(left, right)

  /**
   * Logical operator to decide which middleware to select based on the predicate.
   */
  def ifThenElse[R, E](
    cond: RequestP[Boolean],
  )(left: HttpMiddleware[R, E], right: HttpMiddleware[R, E]): HttpMiddleware[R, E] =
    Middleware.fromMiddlewareFunctionZIO((method, url, headers) => UIO(if (cond(method, url, headers)) left else right))

  /**
   * Logical operator to decide which middleware to select based on the predicate.
   */
  def ifThenElseZIO[R, E](
    cond: RequestP[ZIO[R, E, Boolean]],
  )(left: HttpMiddleware[R, E], right: HttpMiddleware[R, E]): HttpMiddleware[R, E] =
    Middleware.fromMiddlewareFunctionZIO((method, url, headers) =>
      cond(method, url, headers).map {
        case true  => left
        case false => right
      },
    )

  /**
   * Creates a new middleware using transformation functions
   */
  def make[S](req: (Method, URL, Headers) => S): PartiallyAppliedMake[S] = PartiallyAppliedMake(req)

  /**
   * Creates a new middleware using effectful transformation functions
   */
  def makeZIO[R, E, S](req: (Method, URL, Headers) => ZIO[R, Option[E], S]): PartiallyAppliedMakeZIO[R, E, S] =
    PartiallyAppliedMakeZIO(req)

  /**
   * Creates a middleware that produces a Patch for the Response
   */
  def patch[R, E](f: (Status, Headers) => Patch): HttpMiddleware[R, E] =
    HttpMiddleware.make((_, _, _) => ())((status, headers, _) => f(status, headers))

  /**
   * Creates a middleware that produces a Patch for the Response effectfully.
   */
  def patchZIO[R, E](f: (Status, Headers) => ZIO[R, Option[E], Patch]): HttpMiddleware[R, E] =
    HttpMiddleware.makeZIO((_, _, _) => ZIO.unit)((status, headers, _) => f(status, headers))

  /**
   * Removes the header by name
   */
  def removeHeader(name: String): HttpMiddleware[Any, Nothing] =
    patch((_, _) => Patch.removeHeaders(List(name)))

  /**
   * Runs the effect after the response is produced
   */
  def runAfter[R, E](effect: ZIO[R, E, Any]): HttpMiddleware[R, E] =
    patchZIO((_, _) => effect.mapBoth(Option(_), _ => Patch.empty))

  /**
   * Runs the effect before the request is passed on to the HttpApp on which the middleware is applied.
   */
  def runBefore[R, E](effect: ZIO[R, E, Any]): HttpMiddleware[R, E] =
    HttpMiddleware.makeZIO((_, _, _) => effect.mapError(Option(_)).unit)((_, _, _) => UIO(Patch.empty))

  /**
   * Creates a new middleware that always sets the response status to the provided value
   */
  def status(status: Status): HttpMiddleware[Any, Nothing] = HttpMiddleware.patch((_, _) => Patch.setStatus(status))

  /**
   * Applies the middleware only if the condition function evaluates to true
   */
  def when[R, E](cond: RequestP[Boolean])(middleware: HttpMiddleware[R, E]): HttpMiddleware[R, E] =
    ifThenElse(cond)(middleware, Middleware.identity)

  /**
   * Applies the middleware only when the condition for the headers are true
   */
  def whenHeader[R, E](cond: Headers => Boolean, other: HttpMiddleware[R, E]): HttpMiddleware[R, E] =
    when((_, _, headers) => cond(headers))(other)

  /**
   * Switches control to the app only when the condition for the headers are true
   */
  def whenHeader[R, E](cond: Headers => Boolean, other: HttpApp[R, E]): HttpMiddleware[R, E] =
    when((_, _, headers) => cond(headers))(Middleware.fromApp(other))

  /**
   * Applies the middleware only if the condition function effectfully evaluates to true
   */
  def whenZIO[R, E](cond: RequestP[ZIO[R, E, Boolean]])(middleware: HttpMiddleware[R, E]): HttpMiddleware[R, E] =
    ifThenElseZIO(cond)(middleware, Middleware.identity)

  /**
   * Applies the middleware on an HttpApp
   */

  final case class PartiallyAppliedMake[S](req: (Method, URL, Headers) => S) extends AnyVal {
    def apply(res: (Status, Headers, S) => Patch): HttpMiddleware[Any, Nothing] = {
      Middleware.intercept[Request, Response](
        incoming = request => req(request.method, request.url, request.getHeaders),
      )(
        outgoing = (response, state) => res(response.status, response.getHeaders, state)(response),
      )
    }
  }

  final case class PartiallyAppliedMakeZIO[R, E, S](req: (Method, URL, Headers) => ZIO[R, Option[E], S])
      extends AnyVal {
    def apply[R1 <: R, E1 >: E](res: (Status, Headers, S) => ZIO[R1, Option[E1], Patch]): HttpMiddleware[R1, E1] =
      Middleware
        .interceptZIO[Request, Response]
        .apply[R1, E1, S, Response](
          incoming = request => req(request.method, request.url, request.getHeaders),
        )(
          outgoing = (response, state) => res(response.status, response.getHeaders, state).map(patch => patch(response)),
        )
  }
}
