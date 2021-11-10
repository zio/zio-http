package zhttp.http.middleware

import io.netty.handler.codec.http.HttpHeaderNames
import io.netty.util.AsciiString
import io.netty.util.AsciiString.toLowerCase
import zhttp.http.CORS.DefaultCORSConfig
import zhttp.http._
import zhttp.http.middleware.HttpMiddleware.RequestP
import zio.clock.Clock
import zio.console.Console
import zio.duration.Duration
import zio.{UIO, ZIO, clock, console}

import java.io.IOException

/**
 * Middlewares for HttpApp.
 */
sealed trait HttpMiddleware[-R, +E] { self =>
  def apply[R1 <: R, E1 >: E](app: HttpApp[R1, E1]): HttpApp[R1, E1] = HttpMiddleware.execute(self, app)

  def ++[R1 <: R, E1 >: E](other: HttpMiddleware[R1, E1]): HttpMiddleware[R1, E1] =
    self combine other

  def combine[R1 <: R, E1 >: E](other: HttpMiddleware[R1, E1]): HttpMiddleware[R1, E1] =
    HttpMiddleware.Combine(self, other)

  def race[R1 <: R, E1 >: E](other: HttpMiddleware[R1, E1]): HttpMiddleware[R1, E1] =
    HttpMiddleware.Race(self, other)

  def as[R1 <: R, E1 >: E](app: HttpApp[R1, E1]): HttpMiddleware[R1, E1] =
    HttpMiddleware.Constant(app)

  def when(f: RequestP[Boolean]): HttpMiddleware[R, E] =
    modify((m, u, h) => if (f(m, u, h)) self else HttpMiddleware.identity)

  def <>[R1 <: R, E1](other: HttpMiddleware[R1, E1]): HttpMiddleware[R1, E1] =
    self orElse other

  def orElse[R1 <: R, E1](other: HttpMiddleware[R1, E1]): HttpMiddleware[R1, E1] =
    HttpMiddleware.OrElse(self, other)

  def delay(duration: Duration): HttpMiddleware[R with Clock, E] = {
    self.modifyM((_, _, _) => UIO(self).delay(duration))
  }

  def modify[R1 <: R, E1 >: E](f: RequestP[HttpMiddleware[R1, E1]]): HttpMiddleware[R1, E1] =
    HttpMiddleware.fromMiddlewareFunction((m, u, h) => f(m, u, h))

  def modifyM[R1 <: R, E1 >: E](f: RequestP[ZIO[R1, Option[E1], HttpMiddleware[R1, E1]]]): HttpMiddleware[R1, E1] =
    HttpMiddleware.fromMiddlewareFunctionM((m, u, h) => f(m, u, h))

}

object HttpMiddleware {

  type RequestP[+A] = (Method, URL, List[Header]) => A

  private case object Identity extends HttpMiddleware[Any, Nothing]

  private final case class TransformM[R, E, S](
    req: (Method, URL, List[Header]) => ZIO[R, Option[E], S],
    res: (Status, List[Header], S) => ZIO[R, Option[E], Patch],
  ) extends HttpMiddleware[R, E]

  private final case class Combine[R, E](self: HttpMiddleware[R, E], other: HttpMiddleware[R, E])
      extends HttpMiddleware[R, E]

  private final case class FromFunctionM[R, E](
    f: (Method, URL, List[Header]) => ZIO[R, Option[E], HttpMiddleware[R, E]],
  ) extends HttpMiddleware[R, E]

  private final case class Race[R, E](self: HttpMiddleware[R, E], other: HttpMiddleware[R, E])
      extends HttpMiddleware[R, E]

  private final case class Constant[R, E](app: HttpApp[R, E]) extends HttpMiddleware[R, E]

  private final case class OrElse[R, E](self: HttpMiddleware[R, Any], other: HttpMiddleware[R, E])
      extends HttpMiddleware[R, E]

  final case class PartiallyAppliedMake[S](req: (Method, URL, List[Header]) => S) extends AnyVal {
    def apply(res: (Status, List[Header], S) => Patch): HttpMiddleware[Any, Nothing] =
      TransformM[Any, Nothing, S](
        (method, url, headers) => UIO(req(method, url, headers)),
        (status, headers, state) => UIO(res(status, headers, state)),
      )
  }

  final case class PartiallyAppliedMakeM[R, E, S](req: (Method, URL, List[Header]) => ZIO[R, Option[E], S])
      extends AnyVal {
    def apply[R1 <: R, E1 >: E](res: (Status, List[Header], S) => ZIO[R1, Option[E1], Patch]): HttpMiddleware[R1, E1] =
      TransformM(req, res)
  }

  /**
   * An empty middleware that doesn't do anything
   */
  def identity: HttpMiddleware[Any, Nothing] = Identity

  /**
   * Apply a middleware depending upon an effectful condition
   */
  def when[R,E](cond:RequestP[Boolean])(middleware: HttpMiddleware[R,E]): HttpMiddleware[R,E]= ifThenElse(cond)(middleware,HttpMiddleware.identity)

  /**
   * Apply a middleware depending upon an effectful condition
   */
  def whenM[R,E](cond:RequestP[ZIO[R,E,Boolean]])(middleware: HttpMiddleware[R,E]): HttpMiddleware[R,E]= ifThenElseM(cond)(middleware,HttpMiddleware.identity)

  /**
   * Apply one of the 2 middlewares depending on the condition
   */
  def ifThenElseM[R, E](
    cond: RequestP[ZIO[R, E, Boolean]],
  )(left: HttpMiddleware[R, E], right: HttpMiddleware[R, E]): HttpMiddleware[R, E] =
    HttpMiddleware.FromFunctionM((method, url, headers) =>
      cond(method, url, headers).mapError(Option(_)).map {
        case true  => left
        case false => right
      },
    )

  /**
   * Apply one of the 2 middlewares depending on a effectful condition
   */
  def ifThenElse[R, E](
    cond: RequestP[Boolean],
  )(left: HttpMiddleware[R, E], right: HttpMiddleware[R, E]): HttpMiddleware[R, E] =
    HttpMiddleware.FromFunctionM((method, url, headers) => UIO(if (cond(method, url, headers)) left else right))

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
   * Creates a new constants middleware that always executes the app provided, independent of where the middleware is
   * applied
   */
  def fromApp[R, E](app: HttpApp[R, E]): HttpMiddleware[R, E] = HttpMiddleware.Constant(app)

  /**
   * Creates a new middleware using a function from request parameters to a ZIO of HttpMiddleware
   */
  def fromMiddlewareFunctionM[R, E](f: RequestP[ZIO[R, Option[E], HttpMiddleware[R, E]]]): HttpMiddleware[R, E] =
    HttpMiddleware.FromFunctionM(f)

  /**
   * Creates a new middleware using a function from request parameters to a HttpMiddleware
   */
  def fromMiddlewareFunction[R, E](f: RequestP[HttpMiddleware[R, E]]): HttpMiddleware[R, E] =
    fromMiddlewareFunctionM((method, url, headers) => UIO(f(method, url, headers)))

  /**
   * Add log status, method, url and time taken from req to res
   */
  def debug: HttpMiddleware[Console with Clock, IOException] =
    HttpMiddleware.makeM((method, url, _) => zio.clock.nanoTime.map(start => (method, url, start))) {
      case (status, _, (method, url, start)) =>
        for {
          end <- clock.nanoTime
          _   <- console
            .putStrLn(s"${status.asJava.code()} ${method} ${url.asString} ${(end - start) / 1000000}ms")
            .mapError(Option(_))
        } yield Patch.empty
    }

  /**
   * Runs the effect after the response is produced
   */
  def runAfter[R, E](effect: ZIO[R, E, Any]): HttpMiddleware[R, E] =
    patchM((_, _) => effect.mapBoth(Option(_), _ => Patch.empty))

  /**
   * Runs the effect before the request is passed on to the HttpApp on which the middleware is applied.
   */
  def runBefore[R, E](effect: ZIO[R, E, Any]): HttpMiddleware[R, E] =
    HttpMiddleware.makeM((_, _, _) => effect.mapError(Option(_)).unit)((_, _, _) => UIO(Patch.empty))

  /**
   * Creates a middleware that produces a Patch for the Response effectfully.
   */
  def patchM[R, E](f: (Status, List[Header]) => ZIO[R, Option[E], Patch]): HttpMiddleware[R, E] =
    HttpMiddleware.makeM((_, _, _) => ZIO.unit)((status, headers, _) => f(status, headers))

  /**
   * Creates a middleware that produces a Patch for the Response
   */
  def patch[R, E](f: (Status, List[Header]) => Patch): HttpMiddleware[R, E] =
    HttpMiddleware.make((_, _, _) => ())((status, headers, _) => f(status, headers))

  /**
   * Times out the application with a 408 status code.
   */
  def timeout(duration: Duration): HttpMiddleware[Clock, Nothing] =
    HttpMiddleware.identity.race(HttpMiddleware.fromApp(HttpApp.status(Status.REQUEST_TIMEOUT).delayAfter(duration)))

  /**
   * Adds the provided header and value
   */
  def addHeader(name: String, value: String): HttpMiddleware[Any, Nothing] =
    patch((_, _) => Patch.addHeaders(List(Header(name, value))))

  /**
   * Removes the header by name
   */
  def removeHeader(name: String): HttpMiddleware[Any, Nothing] =
    patch((_, _) => Patch.removeHeaders(List(name)))

  /**
   * Creates a middleware for Cross-Origin Resource Sharing (CORS).
   * @see
   *   https://developer.mozilla.org/en-US/docs/Web/HTTP/CORS
   */
  def cors[R, E](config: CORSConfig = DefaultCORSConfig): HttpMiddleware[R, E] = {
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

    val existingRoutesWithHeaders = HttpMiddleware.make((method, _, headers) => {
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
            HttpApp.fromHttp(
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
   * Applies the middleware on an HttpApp
   */
  private[zhttp] def execute[R, E](mid: HttpMiddleware[R, E], app: HttpApp[R, E]): HttpApp[R, E] =
    mid match {
      case Identity => app

      case TransformM(reqF, resF) =>
        HttpApp.fromOptionFunction { req =>
          for {
            s     <- reqF(req.method, req.url, req.headers)
            res   <- app(req)
            patch <- resF(res.status, res.headers, s)
          } yield patch(res)
        }

      case Combine(self, other) => other(self(app))

      case FromFunctionM(reqF) =>
        HttpApp.fromOptionFunction { req =>
          for {
            output <- reqF(req.method, req.url, req.headers)
            res    <- output(app)(req)
          } yield res
        }

      case Race(self, other) =>
        HttpApp.fromOptionFunction { req =>
          self(app)(req) raceFirst other(app)(req)
        }

      case Constant(self) => self

      case OrElse(self, other) =>
        HttpApp.fromOptionFunction { req =>
          (self(app)(req) orElse other(app)(req)).asInstanceOf[ZIO[R, Option[E], Response[R, E]]]
        }
    }

}
