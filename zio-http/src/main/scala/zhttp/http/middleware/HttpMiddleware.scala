package zhttp.http.middleware

import zhttp.http._
import zio.ZIO

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

  case object Identity extends HttpMiddleware[Any, Nothing]

  case class Transform[S](req: (Method, URL, List[Header]) => S, res: (Status, List[Header], S) => Patch)
      extends HttpMiddleware[Any, Nothing]

  case class TransformM[R, E, S](
    req: (Method, URL, List[Header]) => ZIO[R, E, S],
    res: (Status, List[Header], S) => ZIO[R, E, Patch],
  ) extends HttpMiddleware[R, E]

  case class Combine[R, E](self: HttpMiddleware[R, E], other: HttpMiddleware[R, E]) extends HttpMiddleware[R, E]

  def identity: HttpMiddleware[Any, Nothing] = Identity

  def make[S](req: (Method, URL, List[Header]) => S): PartiallyAppliedMake[S] = PartiallyAppliedMake(req)

  def makeM[R, E, S](req: (Method, URL, List[Header]) => ZIO[R, E, S]): PartiallyAppliedMakeM[R, E, S] =
    PartiallyAppliedMakeM(req)

  final case class PartiallyAppliedMake[S](req: (Method, URL, List[Header]) => S) extends AnyVal {
    def apply(res: (Status, List[Header], S) => Patch): HttpMiddleware[Any, Nothing] =
      Transform(req, res)
  }

  final case class PartiallyAppliedMakeM[R, E, S](req: (Method, URL, List[Header]) => ZIO[R, E, S]) extends AnyVal {
    def apply[R1 <: R, E1 >: E](res: (Status, List[Header], S) => ZIO[R1, E1, Patch]): HttpMiddleware[R1, E1] =
      TransformM(req, res)
  }
}
