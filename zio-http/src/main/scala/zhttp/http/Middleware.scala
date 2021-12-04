package zhttp.http

import io.netty.handler.codec.http.HttpHeaderNames
import io.netty.util.AsciiString
import io.netty.util.AsciiString.toLowerCase
import zhttp.http.CORS.DefaultCORSConfig
import zhttp.http.HeaderExtension.Only
import zhttp.http.Middleware.{Flag, RequestP}
import zio.clock.Clock
import zio.console.Console
import zio.duration.Duration
import zio.{UIO, ZIO, clock, console}

import java.io.IOException

/**
 * Middlewares for Http.
 */
sealed trait Middleware[-R, +E] { self =>
  final def <>[R1 <: R, E1](other: Middleware[R1, E1]): Middleware[R1, E1] =
    self orElse other

  final def ++[R1 <: R, E1 >: E](other: Middleware[R1, E1]): Middleware[R1, E1] =
    self combine other

  final def apply[R1 <: R, E1 >: E](app: HttpApp[R1, E1]): HttpApp[R1, E1] = self.execute(app, Middleware.Flag())

  final def as[R1 <: R, E1 >: E](app: HttpApp[R1, E1]): Middleware[R1, E1] =
    Middleware.Constant(app)

  final def combine[R1 <: R, E1 >: E](other: Middleware[R1, E1]): Middleware[R1, E1] =
    Middleware.Combine(self, other)

  final def delay(duration: Duration): Middleware[R with Clock, E] = {
    self.modifyM((_, _, _) => UIO(self).delay(duration))
  }

  final def execute[R1 <: R, E1 >: E](app: HttpApp[R1, E1], flags: Flag): HttpApp[R1, E1] =
    Middleware.execute(self, app, flags)

  final def modify[R1 <: R, E1 >: E](f: RequestP[Middleware[R1, E1]]): Middleware[R1, E1] =
    Middleware.fromMiddlewareFunction((m, u, h) => f(m, u, h))

  final def modifyM[R1 <: R, E1 >: E](
    f: RequestP[ZIO[R1, Option[E1], Middleware[R1, E1]]],
  ): Middleware[R1, E1] =
    Middleware.fromMiddlewareFunctionM((m, u, h) => f(m, u, h))

  final def orElse[R1 <: R, E1](other: Middleware[R1, E1]): Middleware[R1, E1] =
    Middleware.OrElse(self, other)

  final def race[R1 <: R, E1 >: E](other: Middleware[R1, E1]): Middleware[R1, E1] =
    Middleware.Race(self, other)

  final def setEmpty(flag: Boolean): Middleware[R, E] = Middleware.EmptyFlag(self, flag)

  final def when(f: RequestP[Boolean]): Middleware[R, E] =
    modify((m, u, h) => if (f(m, u, h)) self else Middleware.identity)

  final def withEmpty: Middleware[R, E] = self.setEmpty(true)

  final def withoutEmpty: Middleware[R, E] = self.setEmpty(false)
}

object Middleware {

  /**
   * Sets cookie in response headers
   */
  def addCookie(cookie: Cookie): Middleware[Any, Nothing] = Middleware.addHeader(Header.setCookie(cookie))

  /**
   * Adds the provided header and value to the response
   */
  def addHeader(name: String, value: String): Middleware[Any, Nothing] =
    patch((_, _) => Patch.addHeaders(List(Header(name, value))))

  /**
   * Adds the provided header to the response
   */
  def addHeader(header: Header): Middleware[Any, Nothing] =
    patch((_, _) => Patch.addHeaders(List(header)))

  /**
   * Adds the provided list of headers to the response
   */
  def addHeaders(headers: List[Header]): Middleware[Any, Nothing] =
    patch((_, _) => Patch.addHeaders(headers))

  /**
   * Creates an authentication middleware that only allows authenticated requests to be passed on to the app.
   */
  def auth(verify: List[Header] => Boolean, responseHeaders: List[Header] = Nil): Middleware[Any, Nothing] =
    ifThenElse((_, _, h) => verify(h))(
      Middleware.identity,
      Middleware.status(Status.FORBIDDEN) ++ Middleware.addHeaders(responseHeaders),
    )

  /**
   * Creates a middleware for basic authentication
   */
  def basicAuth[R, E](f: (String, String) => Boolean): Middleware[R, E] =
    auth(
      { headers =>
        HeaderExtension(headers).getBasicAuthorizationCredentials match {
          case Some((username, password)) => f(username, password)
          case None                       => false
        }
      },
      List(Header(HttpHeaderNames.WWW_AUTHENTICATE, HeaderExtension.BasicSchemeName)),
    )

  /**
   * Creates a middleware for basic authentication that checks if the credentials are same as the ones given
   */
  def basicAuth[R, E](u: String, p: String): Middleware[R, E] =
    basicAuth((user, password) => (user == u) && (password == p))

  /**
   * Creates a middleware for Cross-Origin Resource Sharing (CORS).
   * @see
   *   https://developer.mozilla.org/en-US/docs/Web/HTTP/CORS
   */
  def cors[R, E](config: CORSConfig = DefaultCORSConfig): Middleware[R, E] = {
    def equalsIgnoreCase(a: Char, b: Char)                                 = a == b || toLowerCase(a) == toLowerCase(b)
    def contentEqualsIgnoreCase(a: CharSequence, b: CharSequence): Boolean = {
      if (a == b)
        true
      else if (a.length() != b.length())
        false
      else if (a.isInstanceOf[AsciiString]) {
        a.asInstanceOf[AsciiString].contentEqualsIgnoreCase(b)
      } else if (b.isInstanceOf[AsciiString]) {
        b.asInstanceOf[AsciiString].contentEqualsIgnoreCase(a)
      } else {
        (0 until a.length()).forall(i => equalsIgnoreCase(a.charAt(i), b.charAt(i)))
      }
    }
    def getHeader(headers: List[Header], headerName: CharSequence): Option[Header]      =
      headers.find(h => contentEqualsIgnoreCase(h.name, headerName))
    def allowCORS(origin: Header, acrm: Method): Boolean                                =
      (config.anyOrigin, config.anyMethod, origin.value.toString, acrm) match {
        case (true, true, _, _)           => true
        case (true, false, _, acrm)       =>
          config.allowedMethods.exists(_.contains(acrm))
        case (false, true, origin, _)     => config.allowedOrigins(origin)
        case (false, false, origin, acrm) =>
          config.allowedMethods.exists(_.contains(acrm)) &&
            config.allowedOrigins(origin)
      }
    def corsHeaders(origin: Header, method: Method, isPreflight: Boolean): List[Header] = {
      (method match {
        case _ if isPreflight =>
          config.allowedHeaders.fold(List.empty[Header])((h: Set[String]) => {
            List(
              Header.custom(
                HttpHeaderNames.ACCESS_CONTROL_ALLOW_HEADERS.toString(),
                h.mkString(","),
              ),
            )
          })
        case _                =>
          config.exposedHeaders.fold(List.empty[Header])(h => {
            List(
              Header.custom(
                HttpHeaderNames.ACCESS_CONTROL_EXPOSE_HEADERS.toString(),
                h.mkString(","),
              ),
            )
          })
      }) ++
        List(
          Header.custom(HttpHeaderNames.ACCESS_CONTROL_ALLOW_ORIGIN.toString(), origin.value),
          Header.custom(
            HttpHeaderNames.ACCESS_CONTROL_ALLOW_METHODS.toString(),
            config.allowedMethods.fold(method.toString())(m => m.map(m => m.toString()).mkString(",")),
          ),
        ) ++
        (if (config.allowCredentials)
           List(
             Header
               .custom(HttpHeaderNames.ACCESS_CONTROL_ALLOW_CREDENTIALS.toString(), config.allowCredentials.toString),
           )
         else List.empty[Header])
    }

    val existingRoutesWithHeaders = Middleware.make((method, _, headers) => {
      (
        method,
        getHeader(headers, HttpHeaderNames.ORIGIN),
      ) match {
        case (_, Some(origin)) if allowCORS(origin, method) => (Some(origin), method)
        case _                                              => (None, method)
      }
    })((_, _, s) => {
      s match {
        case (Some(origin), method) =>
          Patch.addHeaders(corsHeaders(origin, method, isPreflight = false))
        case _                      => Patch.empty
      }
    })

    val optionsHeaders = fromMiddlewareFunction { case (method, _, headers) =>
      (
        method,
        getHeader(headers, HttpHeaderNames.ORIGIN),
        getHeader(headers, HttpHeaderNames.ACCESS_CONTROL_REQUEST_METHOD),
      ) match {
        case (Method.OPTIONS, Some(origin), Some(acrm)) if allowCORS(origin, Method.fromString(acrm.value.toString)) =>
          fromApp(
            (
              Http.succeed(
                Response(
                  Status.NO_CONTENT,
                  headers = corsHeaders(origin, Method.fromString(acrm.value.toString), isPreflight = true),
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
  def debug: Middleware[Console with Clock, IOException] =
    Middleware.makeM((method, url, _) => zio.clock.nanoTime.map(start => (method, url, start))) {
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
  def fromApp[R, E](app: HttpApp[R, E]): Middleware[R, E] = Middleware.Constant(app)

  /**
   * Creates a new middleware using a function from request parameters to a HttpMiddleware
   */
  def fromMiddlewareFunction[R, E](f: RequestP[Middleware[R, E]]): Middleware[R, E] =
    fromMiddlewareFunctionM((method, url, headers) => UIO(f(method, url, headers)))

  /**
   * Creates a new middleware using a function from request parameters to a ZIO of HttpMiddleware
   */
  def fromMiddlewareFunctionM[R, E](f: RequestP[ZIO[R, Option[E], Middleware[R, E]]]): Middleware[R, E] =
    Middleware.FromFunctionM(f)

  /**
   * An empty middleware that doesn't do anything
   */
  def identity: Middleware[Any, Nothing] = Identity

  /**
   * Logical operator to decide which middleware to select based on the header
   */
  def ifHeader[R, E](
    cond: HeaderExtension[Only] => Boolean,
  )(left: Middleware[R, E], right: Middleware[R, E]): Middleware[R, E] =
    ifThenElse((_, _, headers) => cond(HeaderExtension(headers)))(left, right)

  /**
   * Logical operator to decide which middleware to select based on the predicate.
   */
  def ifThenElse[R, E](
    cond: RequestP[Boolean],
  )(left: Middleware[R, E], right: Middleware[R, E]): Middleware[R, E] =
    Middleware.FromFunctionM((method, url, headers) => UIO(if (cond(method, url, headers)) left else right))

  /**
   * Logical operator to decide which middleware to select based on the predicate.
   */
  def ifThenElseM[R, E](
    cond: RequestP[ZIO[R, E, Boolean]],
  )(left: Middleware[R, E], right: Middleware[R, E]): Middleware[R, E] =
    Middleware.FromFunctionM((method, url, headers) =>
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
  def make[S](req: (Method, URL, List[Header]) => S): PartiallyAppliedMake[S] = PartiallyAppliedMake(req)

  /**
   * Creates a new middleware using effectful transformation functions
   */
  def makeM[R, E, S](req: (Method, URL, List[Header]) => ZIO[R, Option[E], S]): PartiallyAppliedMakeM[R, E, S] =
    PartiallyAppliedMakeM(req)

  /**
   * Creates a middleware that produces a Patch for the Response
   */
  def patch[R, E](f: (Status, List[Header]) => Patch): Middleware[R, E] =
    Middleware.make((_, _, _) => ())((status, headers, _) => f(status, headers))

  /**
   * Creates a middleware that produces a Patch for the Response effectfully.
   */
  def patchM[R, E](f: (Status, List[Header]) => ZIO[R, Option[E], Patch]): Middleware[R, E] =
    Middleware.makeM((_, _, _) => ZIO.unit)((status, headers, _) => f(status, headers))

  /**
   * Removes the header by name
   */
  def removeHeader(name: String): Middleware[Any, Nothing] =
    patch((_, _) => Patch.removeHeaders(List(name)))

  /**
   * Runs the effect after the response is produced
   */
  def runAfter[R, E](effect: ZIO[R, E, Any]): Middleware[R, E] =
    patchM((_, _) => effect.mapBoth(Option(_), _ => Patch.empty))

  /**
   * Runs the effect before the request is passed on to the HttpApp on which the middleware is applied.
   */
  def runBefore[R, E](effect: ZIO[R, E, Any]): Middleware[R, E] =
    Middleware.makeM((_, _, _) => effect.mapError(Option(_)).unit)((_, _, _) => UIO(Patch.empty))

  /**
   * Creates a new middleware that always sets the response status to the provided value
   */
  def status(status: Status): Middleware[Any, Nothing] = Middleware.patch((_, _) => Patch.setStatus(status))

  /**
   * Times out the application with a 408 status code.
   */
  def timeout(duration: Duration): Middleware[Clock, Nothing] =
    Middleware.identity.race(Middleware.fromApp(Http.status(Status.REQUEST_TIMEOUT).delayAfter(duration)))

  /**
   * Applies the middleware only if the condition function evaluates to true
   */
  def when[R, E](cond: RequestP[Boolean])(middleware: Middleware[R, E]): Middleware[R, E] =
    ifThenElse(cond)(middleware, Middleware.identity)

  /**
   * Applies the middleware only when the condition for the headers are true
   */
  def whenHeader[R, E](cond: HeaderExtension[Only] => Boolean, other: Middleware[R, E]): Middleware[R, E] =
    when((_, _, headers) => cond(HeaderExtension(headers)))(other)

  /**
   * Switches control to the app only when the condition for the headers are true
   */
  def whenHeader[R, E](cond: HeaderExtension[Only] => Boolean, other: HttpApp[R, E]): Middleware[R, E] =
    when((_, _, headers) => cond(HeaderExtension(headers)))(Middleware.fromApp(other))

  /**
   * Applies the middleware only if the condition function effectfully evaluates to true
   */
  def whenM[R, E](cond: RequestP[ZIO[R, E, Boolean]])(middleware: Middleware[R, E]): Middleware[R, E] =
    ifThenElseM(cond)(middleware, Middleware.identity)

  type RequestP[+A] = (Method, URL, List[Header]) => A

  /**
   * Applies the middleware on an HttpApp
   */
  private[zhttp] def execute[R, E](mid: Middleware[R, E], app: HttpApp[R, E], flag: Flag): HttpApp[R, E] =
    mid match {
      case Identity => app

      case EmptyFlag(mid, status) =>
        execute(mid, app, flag.copy(withEmpty = status))

      case TransformM(reqF, resF) =>
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

      case FromFunctionM(reqF) =>
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
            .asInstanceOf[ZIO[R, Option[E], Response[R, E]]]
        }
    }

  final case class Flag(withEmpty: Boolean = false)

  final case class PartiallyAppliedMake[S](req: (Method, URL, List[Header]) => S) extends AnyVal {
    def apply(res: (Status, List[Header], S) => Patch): Middleware[Any, Nothing] =
      TransformM[Any, Nothing, S](
        (method, url, headers) => UIO(req(method, url, headers)),
        (status, headers, state) => UIO(res(status, headers, state)),
      )
  }

  final case class PartiallyAppliedMakeM[R, E, S](req: (Method, URL, List[Header]) => ZIO[R, Option[E], S])
      extends AnyVal {
    def apply[R1 <: R, E1 >: E](res: (Status, List[Header], S) => ZIO[R1, Option[E1], Patch]): Middleware[R1, E1] =
      TransformM(req, res)
  }

  private final case class EmptyFlag[R, E](mid: Middleware[R, E], status: Boolean) extends Middleware[R, E]

  private final case class TransformM[R, E, S](
    req: (Method, URL, List[Header]) => ZIO[R, Option[E], S],
    res: (Status, List[Header], S) => ZIO[R, Option[E], Patch],
  ) extends Middleware[R, E]

  private final case class Combine[R, E](self: Middleware[R, E], other: Middleware[R, E]) extends Middleware[R, E]

  private final case class FromFunctionM[R, E](
    f: (Method, URL, List[Header]) => ZIO[R, Option[E], Middleware[R, E]],
  ) extends Middleware[R, E]

  private final case class Race[R, E](self: Middleware[R, E], other: Middleware[R, E]) extends Middleware[R, E]

  private final case class Constant[R, E](app: HttpApp[R, E]) extends Middleware[R, E]

  private final case class OrElse[R, E](self: Middleware[R, Any], other: Middleware[R, E]) extends Middleware[R, E]

  private case object Identity extends Middleware[Any, Nothing]
}
