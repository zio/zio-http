package zhttp.http

import io.netty.handler.codec.http.HttpHeaderNames
import zhttp.http.CORS.DefaultCORSConfig
import zhttp.http.Headers.BasicSchemeName
import zhttp.http.HttpMiddleware.{Flag, RequestP}
import zio.clock.Clock
import zio.console.Console
import zio.duration.Duration
import zio.{UIO, ZIO, clock, console}

import java.io.IOException
import java.util.UUID

/**
 * Middlewares for Http.
 */
sealed trait HttpMiddleware[-R, +E] { self =>
  final def <>[R1 <: R, E1](other: HttpMiddleware[R1, E1]): HttpMiddleware[R1, E1] =
    self orElse other

  final def ++[R1 <: R, E1 >: E](other: HttpMiddleware[R1, E1]): HttpMiddleware[R1, E1] =
    self combine other

  final def apply[R1 <: R, E1 >: E](app: HttpApp[R1, E1]): HttpApp[R1, E1] = self.execute(app, HttpMiddleware.Flag())

  final def as[R1 <: R, E1 >: E](app: HttpApp[R1, E1]): HttpMiddleware[R1, E1] =
    HttpMiddleware.Constant(app)

  final def combine[R1 <: R, E1 >: E](other: HttpMiddleware[R1, E1]): HttpMiddleware[R1, E1] =
    HttpMiddleware.Combine(self, other)

  final def delay(duration: Duration): HttpMiddleware[R with Clock, E] = {
    self.modifyZIO((_, _, _) => UIO(self).delay(duration))
  }

  final def execute[R1 <: R, E1 >: E](app: HttpApp[R1, E1], flags: Flag): HttpApp[R1, E1] =
    HttpMiddleware.execute(self, app, flags)

  final def modify[R1 <: R, E1 >: E](f: RequestP[HttpMiddleware[R1, E1]]): HttpMiddleware[R1, E1] =
    HttpMiddleware.fromMiddlewareFunction((m, u, h) => f(m, u, h))

  final def modifyZIO[R1 <: R, E1 >: E](
    f: RequestP[ZIO[R1, Option[E1], HttpMiddleware[R1, E1]]],
  ): HttpMiddleware[R1, E1] =
    HttpMiddleware.fromMiddlewareFunctionZIO((m, u, h) => f(m, u, h))

  final def orElse[R1 <: R, E1](other: HttpMiddleware[R1, E1]): HttpMiddleware[R1, E1] =
    HttpMiddleware.OrElse(self, other)

  final def race[R1 <: R, E1 >: E](other: HttpMiddleware[R1, E1]): HttpMiddleware[R1, E1] =
    HttpMiddleware.Race(self, other)

  final def setEmpty(flag: Boolean): HttpMiddleware[R, E] = HttpMiddleware.EmptyFlag(self, flag)

  final def when(f: RequestP[Boolean]): HttpMiddleware[R, E] =
    modify((m, u, h) => if (f(m, u, h)) self else HttpMiddleware.identity)

  final def withEmpty: HttpMiddleware[R, E] = self.setEmpty(true)

  final def withoutEmpty: HttpMiddleware[R, E] = self.setEmpty(false)
}

object HttpMiddleware {

  type RequestP[+A] = (Method, URL, Headers) => A

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
      HttpMiddleware.identity,
      HttpMiddleware.Constant(Http.status(Status.FORBIDDEN).addHeaders(responseHeaders)),
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
   *   [[Middleware#csrfGenerate]] - Sets cookie with CSRF token
   * @see
   *   [[Middleware#csrfValidate]] - Validate token value in request headers against value in cookies
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
      HttpMiddleware.Constant(Http.status(Status.FORBIDDEN)),
    )
  }

  /**
   * Creates a middleware for Cross-Origin Resource Sharing (CORS).
   * @see
   *   https://developer.mozilla.org/en-US/docs/Web/HTTP/CORS
   */
  def cors[R, E](config: CORSConfig = DefaultCORSConfig): HttpMiddleware[R, E] = {
    def allowCORS(origin: Header, acrm: Method): Boolean                           =
      (config.anyOrigin, config.anyMethod, origin._2.toString, acrm) match {
        case (true, true, _, _)           => true
        case (true, false, _, acrm)       =>
          config.allowedMethods.exists(_.contains(acrm))
        case (false, true, origin, _)     => config.allowedOrigins(origin)
        case (false, false, origin, acrm) =>
          config.allowedMethods.exists(_.contains(acrm)) &&
          config.allowedOrigins(origin)
      }
    def corsHeaders(origin: Header, method: Method, isPreflight: Boolean): Headers = {
      Headers.ifThenElse(isPreflight)(
        onTrue = config.allowedHeaders.fold(Headers.empty) { h =>
          Headers(HttpHeaderNames.ACCESS_CONTROL_ALLOW_HEADERS.toString(), h.mkString(","))
        },
        onFalse = config.exposedHeaders.fold(Headers.empty) { h =>
          Headers(HttpHeaderNames.ACCESS_CONTROL_EXPOSE_HEADERS.toString(), h.mkString(","))
        },
      ) ++
        Headers(HttpHeaderNames.ACCESS_CONTROL_ALLOW_ORIGIN.toString(), origin._2) ++
        Headers(
          HttpHeaderNames.ACCESS_CONTROL_ALLOW_METHODS.toString(),
          config.allowedMethods.fold(method.toString())(m => m.map(m => m.toString()).mkString(",")),
        ) ++
        Headers.when(config.allowCredentials) {
          Headers(HttpHeaderNames.ACCESS_CONTROL_ALLOW_CREDENTIALS, config.allowCredentials.toString)
        }
    }

    val existingRoutesWithHeaders = HttpMiddleware.make((method, _, headers) => {
      (
        method,
        headers.getHeader(HttpHeaderNames.ORIGIN),
      ) match {
        case (_, Some(origin)) if allowCORS(origin, method) => (Some(origin), method)
        case _                                              => (None, method)
      }
    })((_, _, s) => {
      s match {
        case (Some(origin), method) =>
          Patch.addHeader(corsHeaders(origin, method, isPreflight = false))
        case _                      => Patch.empty
      }
    })

    val optionsHeaders = fromMiddlewareFunction { case (method, _, headers) =>
      (
        method,
        headers.getHeader(HttpHeaderNames.ORIGIN),
        headers.getHeader(HttpHeaderNames.ACCESS_CONTROL_REQUEST_METHOD),
      ) match {
        case (Method.OPTIONS, Some(origin), Some(acrm)) if allowCORS(origin, Method.fromString(acrm._2.toString)) =>
          fromApp(
            (
              Http.succeed(
                Response(
                  Status.NO_CONTENT,
                  headers = corsHeaders(origin, Method.fromString(acrm._2.toString), isPreflight = true),
                ),
              ),
            ),
          )
        case _ => identity
      }
    }

    existingRoutesWithHeaders orElse optionsHeaders
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
   * Creates a new constants middleware that always executes the app provided, independent of where the middleware is
   * applied
   */
  def fromApp[R, E](app: HttpApp[R, E]): HttpMiddleware[R, E] = HttpMiddleware.Constant(app)

  /**
   * Creates a new middleware using a function from request parameters to a HttpMiddleware
   */
  def fromMiddlewareFunction[R, E](f: RequestP[HttpMiddleware[R, E]]): HttpMiddleware[R, E] =
    fromMiddlewareFunctionZIO((method, url, headers) => UIO(f(method, url, headers)))

  /**
   * Creates a new middleware using a function from request parameters to a ZIO of HttpMiddleware
   */
  def fromMiddlewareFunctionZIO[R, E](f: RequestP[ZIO[R, Option[E], HttpMiddleware[R, E]]]): HttpMiddleware[R, E] =
    HttpMiddleware.FromFunctionZIO(f)

  /**
   * An empty middleware that doesn't do anything
   */
  def identity: HttpMiddleware[Any, Nothing] = Identity

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
    HttpMiddleware.FromFunctionZIO((method, url, headers) => UIO(if (cond(method, url, headers)) left else right))

  /**
   * Logical operator to decide which middleware to select based on the predicate.
   */
  def ifThenElseZIO[R, E](
    cond: RequestP[ZIO[R, E, Boolean]],
  )(left: HttpMiddleware[R, E], right: HttpMiddleware[R, E]): HttpMiddleware[R, E] =
    HttpMiddleware.FromFunctionZIO((method, url, headers) =>
      cond(method, url, headers).mapBoth(
        Option(_),
        {
          case true  => left
          case false => right
        },
      ),
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
   * Times out the application with a 408 status code.
   */
  def timeout(duration: Duration): HttpMiddleware[Clock, Nothing] =
    HttpMiddleware.identity.race(HttpMiddleware.fromApp(Http.status(Status.REQUEST_TIMEOUT).delayAfter(duration)))

  /**
   * Applies the middleware only if the condition function evaluates to true
   */
  def when[R, E](cond: RequestP[Boolean])(middleware: HttpMiddleware[R, E]): HttpMiddleware[R, E] =
    ifThenElse(cond)(middleware, HttpMiddleware.identity)

  /**
   * Applies the middleware only when the condition for the headers are true
   */
  def whenHeader[R, E](cond: Headers => Boolean, other: HttpMiddleware[R, E]): HttpMiddleware[R, E] =
    when((_, _, headers) => cond(headers))(other)

  /**
   * Switches control to the app only when the condition for the headers are true
   */
  def whenHeader[R, E](cond: Headers => Boolean, other: HttpApp[R, E]): HttpMiddleware[R, E] =
    when((_, _, headers) => cond(headers))(HttpMiddleware.fromApp(other))

  /**
   * Applies the middleware only if the condition function effectfully evaluates to true
   */
  def whenZIO[R, E](cond: RequestP[ZIO[R, E, Boolean]])(middleware: HttpMiddleware[R, E]): HttpMiddleware[R, E] =
    ifThenElseZIO(cond)(middleware, HttpMiddleware.identity)

  /**
   * Applies the middleware on an HttpApp
   */
  private[zhttp] def execute[R, E](mid: HttpMiddleware[R, E], app: HttpApp[R, E], flag: Flag): HttpApp[R, E] =
    mid match {
      case Identity => app

      case EmptyFlag(mid, status) =>
        execute(mid, app, flag.copy(withEmpty = status))

      case TransformZIO(reqF, resF) =>
        Http.fromOptionFunction { req =>
          for {
            s     <- reqF(req.method, req.url, req.getHeaders)
            res   <-
              if (flag.withEmpty) app(req).catchSome { case None => UIO(Response.status(Status.NOT_FOUND)) }
              else app(req)
            patch <- resF(res.status, res.getHeaders, s)
          } yield patch(res)
        }

      case Combine(self, other) => other.execute(self.execute(app, flag), flag)

      case FromFunctionZIO(reqF) =>
        Http.fromOptionFunction { req =>
          for {
            output <- reqF(req.method, req.url, req.getHeaders)
            res    <- output.execute(app, flag)(req)
          } yield res
        }

      case Race(self, other) =>
        Http.fromOptionFunction { req =>
          self.execute(app, flag)(req) raceFirst other.execute(app, flag)(req)
        }

      case Constant(self) => self

      case OrElse(self, other) =>
        Http.fromOptionFunction { req =>
          (self.execute(app, flag)(req) orElse other.execute(app, flag)(req))
            .asInstanceOf[ZIO[R, Option[E], Response]]
        }
    }

  final case class Flag(withEmpty: Boolean = false)

  final case class PartiallyAppliedMake[S](req: (Method, URL, Headers) => S) extends AnyVal {
    def apply(res: (Status, Headers, S) => Patch): HttpMiddleware[Any, Nothing] =
      TransformZIO[Any, Nothing, S](
        (method, url, headers) => UIO(req(method, url, headers)),
        (status, headers, state) => UIO(res(status, headers, state)),
      )
  }

  final case class PartiallyAppliedMakeZIO[R, E, S](req: (Method, URL, Headers) => ZIO[R, Option[E], S])
      extends AnyVal {
    def apply[R1 <: R, E1 >: E](res: (Status, Headers, S) => ZIO[R1, Option[E1], Patch]): HttpMiddleware[R1, E1] =
      TransformZIO(req, res)
  }

  private final case class EmptyFlag[R, E](mid: HttpMiddleware[R, E], status: Boolean) extends HttpMiddleware[R, E]

  private final case class TransformZIO[R, E, S](
    req: (Method, URL, Headers) => ZIO[R, Option[E], S],
    res: (Status, Headers, S) => ZIO[R, Option[E], Patch],
  ) extends HttpMiddleware[R, E]

  private final case class Combine[R, E](self: HttpMiddleware[R, E], other: HttpMiddleware[R, E])
      extends HttpMiddleware[R, E]

  private final case class FromFunctionZIO[R, E](
    f: (Method, URL, Headers) => ZIO[R, Option[E], HttpMiddleware[R, E]],
  ) extends HttpMiddleware[R, E]

  private final case class Race[R, E](self: HttpMiddleware[R, E], other: HttpMiddleware[R, E])
      extends HttpMiddleware[R, E]

  private final case class Constant[R, E](app: HttpApp[R, E]) extends HttpMiddleware[R, E]

  private final case class OrElse[R, E](self: HttpMiddleware[R, Any], other: HttpMiddleware[R, E])
      extends HttpMiddleware[R, E]

  private case object Identity extends HttpMiddleware[Any, Nothing]
}
