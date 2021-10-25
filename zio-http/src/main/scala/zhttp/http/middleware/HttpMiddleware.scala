package zhttp.http.middleware

import zhttp.http._

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

  case class Transform[R, E, S](req: RequestMiddleware[R, E, S], res: ResponseMiddleware[R, E, S])
      extends HttpMiddleware[R, E]

  case class Combine[R, E](self: HttpMiddleware[R, E], other: HttpMiddleware[R, E]) extends HttpMiddleware[R, E]

  def identity: HttpMiddleware[Any, Nothing] = Identity

  def make[R, E, S](req: RequestMiddleware[R, E, S], res: ResponseMiddleware[R, E, S]): HttpMiddleware[R, E] =
    Transform(req, res)
}
