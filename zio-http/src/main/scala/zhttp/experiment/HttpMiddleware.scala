package zhttp.experiment

import zhttp.http.HttpApp
import zio.{UIO, ZIO}

/**
 * Middlewares for HttpApp.
 */

sealed trait HttpMiddleware[-R, +E] { self =>
  def run[R1 <: R, E1 >: E](app: HttpApp[R1, E1]): HttpApp[R1, E1] = ???

  def apply[R1 <: R, E1 >: E](app: HttpApp[R1, E1]): HttpApp[R1, E1] = run(app)

  def ++[R1 <: R, E1 >: E](other: HttpMiddleware[R1, E1]): HttpMiddleware[R1, E1] =
    self combine other

  def combine[R1 <: R, E1 >: E](other: HttpMiddleware[R1, E1]): HttpMiddleware[R1, E1] =
    HttpMiddleware.Combine(self, other)
}

object HttpMiddleware {
  type Request
  type Response

  case class Transform[R, E, S](
    req: Request => ZIO[R, E, (Request, S)],
    res: (Request, Response, S) => ZIO[R, E, Response],
  ) extends HttpMiddleware[R, E]

  case class Combine[R, E](self: HttpMiddleware[R, E], other: HttpMiddleware[R, E]) extends HttpMiddleware[R, E]

  def identity: HttpMiddleware[Any, Nothing] = state[Unit](req => (req, ()), (_, res, _) => res)

  def stateM[S]: PartiallyAppliedCollectM[S] = PartiallyAppliedCollectM(())

  def state[S]: PartiallyAppliedCollect[S] = PartiallyAppliedCollect(())

  def request(f: Request => Request): HttpMiddleware[Any, Nothing] =
    state[Unit](req => (f(req), ()), (_, res, _) => res)

  def requestM[R, E](f: Request => ZIO[R, E, Request]): HttpMiddleware[R, E] =
    stateM[Unit](req => f(req).map((_, ())), (_, res, _) => UIO(res))

  def tapM[S]: PartiallyAppliedTapM[S] = PartiallyAppliedTapM(())

  def tap[S]: PartiallyAppliedTap[S] = PartiallyAppliedTap(())

  final case class PartiallyAppliedTapM[S](unit: Unit) extends AnyVal {
    def apply[R, E](f: Request => ZIO[R, E, S], g: (Request, Response, S) => ZIO[R, E, Any]): HttpMiddleware[R, E] =
      HttpMiddleware.stateM[S](req => f(req).map(s => (req, s)), (req, res, s) => g(req, res, s).as(res))
  }

  final case class PartiallyAppliedTap[S](unit: Unit) extends AnyVal {
    def apply(f: Request => S, g: (Request, Response, S) => Any): HttpMiddleware[Any, Nothing] =
      HttpMiddleware.stateM[S](req => UIO((req, f(req))), (req, res, s) => UIO(g(req, res, s)).as(res))
  }

  final case class PartiallyAppliedCollectM[S](unit: Unit) extends AnyVal {
    def apply[R, E](
      f: Request => ZIO[R, E, (Request, S)],
      g: (Request, Response, S) => ZIO[R, E, Response],
    ): HttpMiddleware[R, E] = Transform(f, g)
  }

  final case class PartiallyAppliedCollect[S](unit: Unit) extends AnyVal {
    def apply(f: Request => (Request, S), g: (Request, Response, S) => Response): HttpMiddleware[Any, Nothing] =
      stateM[S](req => UIO(f(req)), (req, res, s) => UIO(g(req, res, s)))
  }
}
