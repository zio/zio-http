package zhttp.http

import io.netty.handler.codec.http.HttpHeaderNames
import zhttp.http.CORS.DefaultCORSConfig
import zhttp.http.Headers.BasicSchemeName
import zio.clock.Clock
import zio.console.Console
import zio.duration.Duration
import zio.{UIO, ZIO, clock, console}

import java.io.IOException
import java.util.UUID

/**
 * Middlewares are essentially transformations that one can apply on any Http to produce a new one. They can modify
 * requests and responses and also transform them into more concrete domain entities.
 */
sealed trait Middleware[-R, +E, +AIn, -BIn, -AOut, +BOut] { self =>
  final def <>[R1 <: R, E1, AIn0 >: AIn, BIn0 <: BIn, AOut0 <: AOut, BOut0 >: BOut](
    other: Middleware[R1, E1, AIn0, BIn0, AOut0, BOut0],
  ): Middleware[R1, E1, AIn0, BIn0, AOut0, BOut0] = self orElse other

  final def <<<[R1 <: R, E1 >: E, A0 <: AOut, B0 >: BOut, A1, B1](
    other: Middleware[R1, E1, A0, B0, A1, B1],
  ): Middleware[R1, E1, AIn, BIn, A1, B1] = self compose other

  final def ++[R1 <: R, E1 >: E, A0 >: AIn <: AOut, B0 >: BOut <: BIn](
    other: Middleware[R1, E1, A0, B0, A0, B0],
  ): Middleware[R1, E1, A0, B0, A0, B0] =
    self combine other

  final def apply[R1 <: R, E1 >: E](http: Http[R1, E1, AIn, BIn]): Http[R1, E1, AOut, BOut] = execute(http)

  final def combine[R1 <: R, E1 >: E, A0 >: AIn <: AOut, B0 >: BOut <: BIn](
    other: Middleware[R1, E1, A0, B0, A0, B0],
  ): Middleware[R1, E1, A0, B0, A0, B0] =
    self compose other

  final def compose[R1 <: R, E1 >: E, A0 <: AOut, B0 >: BOut, A1, B1](
    other: Middleware[R1, E1, A0, B0, A1, B1],
  ): Middleware[R1, E1, AIn, BIn, A1, B1] = Middleware.Compose(self, other)

  final def delay(duration: Duration): Middleware[R with Clock, E, AIn, BIn, AOut, BOut] =
    self.mapZIO(b => UIO(b).delay(duration))

  final def flatMap[R1 <: R, E1 >: E, AIn0 >: AIn, BIn0 <: BIn, AOut0 <: AOut, BOut0](
    f: BOut => Middleware[R1, E1, AIn0, BIn0, AOut0, BOut0],
  ): Middleware[R1, E1, AIn0, BIn0, AOut0, BOut0] =
    Middleware.FlatMap(self, f)

  final def flatten[R1 <: R, E1 >: E, AIn0 >: AIn, BIn0 <: BIn, AOut0 <: AOut, BOut0](implicit
    ev: BOut <:< Middleware[R1, E1, AIn0, BIn0, AOut0, BOut0],
  ): Middleware[R1, E1, AIn0, BIn0, AOut0, BOut0] =
    flatMap(identity(_))

  final def map[BOut0](f: BOut => BOut0): Middleware[R, E, AIn, BIn, AOut, BOut0] =
    self.flatMap(b => Middleware.succeed(f(b)))

  final def mapZIO[R1 <: R, E1 >: E, BOut0](f: BOut => ZIO[R1, E1, BOut0]): Middleware[R1, E1, AIn, BIn, AOut, BOut0] =
    self.flatMap(b => Middleware.fromHttp(Http.fromEffect(f(b))))

  final def orElse[R1 <: R, E1, AIn0 >: AIn, BIn0 <: BIn, AOut0 <: AOut, BOut0 >: BOut](
    other: Middleware[R1, E1, AIn0, BIn0, AOut0, BOut0],
  ): Middleware[R1, E1, AIn0, BIn0, AOut0, BOut0] =
    Middleware.OrElse(self, other)

  final def race[R1 <: R, E1 >: E, AIn1 >: AIn, BIn1 <: BIn, AOut1 <: AOut, BOut1 >: BOut](
    other: Middleware[R1, E1, AIn1, BIn1, AOut1, BOut1],
  ): Middleware[R1, E1, AIn1, BIn1, AOut1, BOut1] =
    Middleware.Race(self, other)

  final def when[AOut0 <: AOut](cond: AOut0 => Boolean): Middleware[R, E, AIn, BIn, AOut0, BOut] =
    Middleware.ifThenElse[AOut0](cond(_))(
      isTrue = _ => self,
      isFalse = _ => Middleware.identity,
    )

  private[zhttp] final def execute[R1 <: R, E1 >: E](http: Http[R1, E1, AIn, BIn]): Http[R1, E1, AOut, BOut] =
    Middleware.execute(http, self)
}

object Middleware {

  def codec[A, B]: PartialCodec[A, B] = new PartialCodec[A, B](())

  def codecZIO[A, B]: PartialCodecZIO[A, B] = new PartialCodecZIO[A, B](())

  def fail[E](e: E): Middleware[Any, E, Nothing, Any, Any, Nothing] = Fail(e)

  def fromHttp[R, E, A, B](http: Http[R, E, A, B]): Middleware[R, E, Nothing, Any, A, B] = Constant(http)

  /**
   * Logical operator to decide which middleware to select based on the predicate.
   */
  def ifThenElse[A]: PartialIfThenElse[A] = new PartialIfThenElse(())

  /**
   * Logical operator to decide which middleware to select based on the predicate.
   */
  def ifThenElse[R, E](
    cond: RequestP[Boolean],
  )(left: HttpMiddleware[R, E], right: HttpMiddleware[R, E]): HttpMiddleware[R, E] =
    Middleware.ifThenElse[Request](req => cond(req.method, req.url, req.getHeaders))(_ => left, _ => right)

  /**
   * Logical operator to decide which middleware to select based on the predicate effect.
   */
  def ifThenElseZIO[A]: PartialIfThenElseZIO[A] = new PartialIfThenElseZIO(())

  /**
   * Logical operator to decide which middleware to select based on the predicate.
   */
  def ifThenElseZIO[R, E](
    cond: RequestP[ZIO[R, E, Boolean]],
  )(left: HttpMiddleware[R, E], right: HttpMiddleware[R, E]): HttpMiddleware[R, E] =
    Middleware.ifThenElseZIO[Request](req => cond(req.method, req.url, req.getHeaders))(_ => left, _ => right)

  def intercept[A, B]: PartialIntercept[A, B] = new PartialIntercept[A, B](())

  def interceptZIO[A, B]: PartialInterceptZIO[A, B] = new PartialInterceptZIO[A, B](())

  def make[A]: PartialMake[A] = new PartialMake[A](())

  def makeZIO[A]: PartialMakeZIO[A] = new PartialMakeZIO[A](())

  def succeed[B](b: B): Middleware[Any, Nothing, Nothing, Any, Any, B] = fromHttp(Http.succeed(b))

  /**
   * Sets cookie in response headers
   */
  def addCookie(cookie: Cookie): HttpMiddleware[Any, Nothing] =
    addHeader(Headers.setCookie(cookie))

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
    Middleware.whenHeader(
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
    makeResponseZIO((method, url, _) => zio.clock.nanoTime.map(start => (method, url, start))) {
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
  def makeResponse[S](req: (Method, URL, Headers) => S): PartialResponseMake[S] = PartialResponseMake(req)

  /**
   * Creates a new middleware using effectful transformation functions
   */
  def makeResponseZIO[R, E, S](req: (Method, URL, Headers) => ZIO[R, Option[E], S]): PartialResponseMakeZIO[R, E, S] =
    PartialResponseMakeZIO(req)

  /**
   * Creates a middleware that produces a Patch for the Response
   */
  def patch[R, E](f: (Status, Headers) => Patch): HttpMiddleware[R, E] =
    makeResponse((_, _, _) => ())((status, headers, _) => f(status, headers))

  /**
   * Creates a middleware that produces a Patch for the Response effectfully.
   */
  def patchZIO[R, E](f: (Status, Headers) => ZIO[R, Option[E], Patch]): HttpMiddleware[R, E] =
    makeResponseZIO((_, _, _) => ZIO.unit)((status, headers, _) => f(status, headers))

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
    makeResponseZIO((_, _, _) => effect.mapError(Option(_)).unit)((_, _, _) => UIO(Patch.empty))

  /**
   * Creates a new middleware that always sets the response status to the provided value
   */
  def status(status: Status): HttpMiddleware[Any, Nothing] = patch((_, _) => Patch.setStatus(status))

  /**
   * Applies the middleware on an HttpApp
   */

  final case class PartialResponseMake[S](req: (Method, URL, Headers) => S) extends AnyVal {
    def apply(res: (Status, Headers, S) => Patch): HttpMiddleware[Any, Nothing] = {
      Middleware.intercept[Request, Response](
        incoming = request => req(request.method, request.url, request.getHeaders),
      )(
        outgoing = (response, state) => res(response.status, response.getHeaders, state)(response),
      )
    }
  }

  final case class PartialResponseMakeZIO[R, E, S](req: (Method, URL, Headers) => ZIO[R, Option[E], S]) extends AnyVal {
    def apply[R1 <: R, E1 >: E](res: (Status, Headers, S) => ZIO[R1, Option[E1], Patch]): HttpMiddleware[R1, E1] =
      Middleware
        .interceptZIO[Request, Response]
        .apply[R1, E1, S, Response](
          incoming = request => req(request.method, request.url, request.getHeaders),
        )(
          outgoing = (response, state) => res(response.status, response.getHeaders, state).map(patch => patch(response)),
        )
  }

  /**
   * Logical operator to decide which middleware to select based on the header
   */
  def ifHeader[R, E](
    cond: Headers => Boolean,
  )(left: HttpMiddleware[R, E], right: HttpMiddleware[R, E]): HttpMiddleware[R, E] =
    Middleware.ifThenElse[Request](req => cond(req.getHeaders))(_ => left, _ => right)

  /**
   * Applies the middleware only if the condition function evaluates to true
   */
  def when[R, E](cond: RequestP[Boolean])(middleware: HttpMiddleware[R, E]): HttpMiddleware[R, E] =
    middleware.when[Request](req => cond(req.method, req.url, req.getHeaders))

  /**
   * Applies the middleware only if the condition function effectfully evaluates to true
   */
  def whenZIO[R, E](cond: RequestP[ZIO[R, E, Boolean]])(middleware: HttpMiddleware[R, E]): HttpMiddleware[R, E] =
    Middleware.ifThenElseZIO[Request](req => cond(req.method, req.url, req.getHeaders))(
      _ => middleware,
      _ => Middleware.identity,
    )

  /**
   * Applies the middleware only when the condition for the headers are true
   */
  def whenHeader[R, E](cond: Headers => Boolean, middleware: HttpMiddleware[R, E]): HttpMiddleware[R, E] =
    middleware.when[Request](req => cond(req.getHeaders))

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
  def fromMiddlewareFunction[R, E](f: RequestP[HttpMiddleware[R, E]]): HttpMiddleware[R, E] =
    Middleware.make(req => f(req.method, req.url, req.getHeaders))

  /**
   * Creates a new middleware using a function from request parameters to a ZIO of HttpMiddleware
   */
  def fromMiddlewareFunctionZIO[R, E](f: RequestP[ZIO[R, E, HttpMiddleware[R, E]]]): HttpMiddleware[R, E] =
    Middleware.makeZIO[Request](req => f(req.method, req.url, req.getHeaders))

  /**
   * An empty middleware that doesn't do anything
   */
  def identity: Middleware[Any, Nothing, Nothing, Any, Any, Nothing] = Middleware.Identity

  /**
   * Creates an authentication middleware that only allows authenticated requests to be passed on to the app.
   */
  def auth(verify: Headers => Boolean, responseHeaders: Headers = Headers.empty): HttpMiddleware[Any, Nothing] =
    Middleware.ifThenElse[Request](req => verify(req.getHeaders))(
      _ => Middleware.identity,
      _ => Middleware.fromHttp(Http.status(Status.FORBIDDEN).addHeaders(responseHeaders)),
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
    Middleware.fromMiddlewareFunction((method, _, headers) => {
      (
        method,
        headers.getHeader(HttpHeaderNames.ORIGIN),
        headers.getHeader(HttpHeaderNames.ACCESS_CONTROL_REQUEST_METHOD),
      ) match {
        case (Method.OPTIONS, Some(origin), Some(acrm)) if allowCORS(origin, Method.fromString(acrm._2.toString)) =>
          fromApp(
            Http.succeed(
              Response(
                Status.NO_CONTENT,
                headers = corsHeaders(origin, Method.fromString(acrm._2.toString), isPreflight = true),
              ),
            ),
          )
        case (_, Some(origin), _) if allowCORS(origin, method)                                                    =>
          Middleware.addHeader(corsHeaders(origin, method, isPreflight = false))
        case _ => Middleware.identity
      }
    })
  }

  private[zhttp] def execute[R, E, AIn, BIn, AOut, BOut](
    http: Http[R, E, AIn, BIn],
    self: Middleware[R, E, AIn, BIn, AOut, BOut],
  ): Http[R, E, AOut, BOut] =
    self match {
      case Codec(decoder, encoder)       => http.contramapZIO(decoder(_)).mapZIO(encoder(_))
      case Identity                      => http.asInstanceOf[Http[R, E, AOut, BOut]]
      case Constant(http)                => http
      case OrElse(self, other)           => self.execute(http).orElse(other.execute(http))
      case Fail(error)                   => Http.fail(error)
      case Compose(self, other)          => other.execute(self.execute(http))
      case FlatMap(self, f)              => self.execute(http).flatMap(f(_).execute(http))
      case Race(self, other)             => self.execute(http) race other.execute(http)
      case Intercept(incoming, outgoing) =>
        Http.fromOptionFunction[AOut] { a =>
          for {
            s <- incoming(a)
            b <- http(a.asInstanceOf[AIn])
            c <- outgoing(b, s)
          } yield c.asInstanceOf[BOut]
        }
    }

  final class PartialMake[AOut](val unit: Unit) extends AnyVal {
    def apply[R, E, AIn, BIn, BOut](
      f: AOut => Middleware[R, E, AIn, BIn, AOut, BOut],
    ): Middleware[R, E, AIn, BIn, AOut, BOut] =
      Middleware.fromHttp(Http.fromFunction[AOut](aout => f(aout))).flatten
  }

  final class PartialMakeZIO[AOut](val unit: Unit) extends AnyVal {
    def apply[R, E, AIn, BIn, BOut](
      f: AOut => ZIO[R, E, Middleware[R, E, AIn, BIn, AOut, BOut]],
    ): Middleware[R, E, AIn, BIn, AOut, BOut] =
      Middleware.fromHttp(Http.fromFunctionZIO[AOut](aout => f(aout))).flatten
  }

  final case class Fail[E](error: E) extends Middleware[Any, E, Nothing, Any, Any, Nothing]

  final case class OrElse[R, E0, E1, AIn, BIn, AOut, BOut](
    self: Middleware[R, E0, AIn, BIn, AOut, BOut],
    other: Middleware[R, E1, AIn, BIn, AOut, BOut],
  ) extends Middleware[R, E1, AIn, BIn, AOut, BOut]

  final class PartialInterceptZIO[A, B](val unit: Unit) extends AnyVal {
    def apply[R, E, S, BOut](incoming: A => ZIO[R, Option[E], S])(
      outgoing: (B, S) => ZIO[R, Option[E], BOut],
    ): Middleware[R, E, A, B, A, BOut] = Intercept(incoming, outgoing)
  }

  final class PartialIntercept[A, B](val unit: Unit) extends AnyVal {
    def apply[S, BOut](incoming: A => S)(outgoing: (B, S) => BOut): Middleware[Any, Nothing, A, B, A, BOut] =
      interceptZIO[A, B](a => UIO(incoming(a)))((b, s) => UIO(outgoing(b, s)))
  }

  final case class Codec[R, E, AIn, BIn, AOut, BOut](decoder: AOut => ZIO[R, E, AIn], encoder: BIn => ZIO[R, E, BOut])
      extends Middleware[R, E, AIn, BIn, AOut, BOut]

  final class PartialCodec[AOut, BIn](val unit: Unit) extends AnyVal {
    def apply[AIn, BOut](decoder: AOut => AIn, encoder: BIn => BOut): Middleware[Any, Nothing, AIn, BIn, AOut, BOut] =
      Codec(a => UIO(decoder(a)), b => UIO(encoder(b)))
  }

  final class PartialIfThenElse[AOut](val unit: Unit) extends AnyVal {
    def apply[R, E, AIn, BIn, BOut](cond: AOut => Boolean)(
      isTrue: AOut => Middleware[R, E, AIn, BIn, AOut, BOut],
      isFalse: AOut => Middleware[R, E, AIn, BIn, AOut, BOut],
    ): Middleware[R, E, AIn, BIn, AOut, BOut] =
      Middleware
        .fromHttp(Http.fromFunction[AOut] { a => if (cond(a)) isTrue(a) else isFalse(a) })
        .flatten
  }

  final class PartialIfThenElseZIO[AOut](val unit: Unit) extends AnyVal {
    def apply[R, E, AIn, BIn, BOut](cond: AOut => ZIO[R, E, Boolean])(
      isTrue: AOut => Middleware[R, E, AIn, BIn, AOut, BOut],
      isFalse: AOut => Middleware[R, E, AIn, BIn, AOut, BOut],
    ): Middleware[R, E, AIn, BIn, AOut, BOut] =
      Middleware
        .fromHttp(Http.fromFunctionZIO[AOut] { a => cond(a).map(b => if (b) isTrue(a) else isFalse(a)) })
        .flatten
  }

  final class PartialCodecZIO[AOut, BIn](val unit: Unit) extends AnyVal {
    def apply[R, E, AIn, BOut](
      decoder: AOut => ZIO[R, E, AIn],
      encoder: BIn => ZIO[R, E, BOut],
    ): Middleware[R, E, AIn, BIn, AOut, BOut] =
      Codec(decoder(_), encoder(_))
  }

  final case class Constant[R, E, AOut, BOut](http: Http[R, E, AOut, BOut])
      extends Middleware[R, E, Nothing, Any, AOut, BOut]

  final case class Intercept[R, E, A, B, S, BOut](
    incoming: A => ZIO[R, Option[E], S],
    outgoing: (B, S) => ZIO[R, Option[E], BOut],
  ) extends Middleware[R, E, A, B, A, BOut]

  final case class Compose[R, E, A0, B0, A1, B1, A2, B2](
    self: Middleware[R, E, A0, B0, A1, B1],
    other: Middleware[R, E, A1, B1, A2, B2],
  ) extends Middleware[R, E, A0, B0, A2, B2]

  final case class FlatMap[R, E, AIn, BIn, AOut, BOut0, BOut1](
    self: Middleware[R, E, AIn, BIn, AOut, BOut0],
    f: BOut0 => Middleware[R, E, AIn, BIn, AOut, BOut1],
  ) extends Middleware[R, E, AIn, BIn, AOut, BOut1]

  final case class Race[R, E, AIn, BIn, AOut, BOut](
    self: Middleware[R, E, AIn, BIn, AOut, BOut],
    other: Middleware[R, E, AIn, BIn, AOut, BOut],
  ) extends Middleware[R, E, AIn, BIn, AOut, BOut]

  case object Identity extends Middleware[Any, Nothing, Nothing, Any, Any, Nothing]
}
