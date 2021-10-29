package zhttp.http.middleware

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
