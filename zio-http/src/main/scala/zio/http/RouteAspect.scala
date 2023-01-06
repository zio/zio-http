package zio.http

import zio.{Trace, ZIO}

trait RouteAspect[-R, +Err, +AIn, -AOut, -BIn, +BOut] { self =>
  def apply[R1 <: R, Err1 >: Err](route: Route[R1, Err1, AIn, AOut])(implicit
    trace: Trace,
  ): Route[R1, Err1, BIn, BOut]
}

object RouteAspect {

  def allow[AIn, AOut]: Allow[AIn, AOut] = new Allow[AIn, AOut](())

  def allowZIO[AIn, AOut]: AllowZIO[AIn, AOut] = new AllowZIO[AIn, AOut](())

  def identity[AIn, AOut]: RouteAspect[Any, Nothing, AIn, AOut, AIn, AOut] =
    new RouteAspect[Any, Nothing, AIn, AOut, AIn, AOut] {
      override def apply[R1 <: Any, Err1 >: Nothing](route: Route[R1, Err1, AIn, AOut])(implicit
        trace: Trace,
      ): Route[R1, Err1, AIn, AOut] =
        route
    }

  final class Allow[AIn, AOut](val unit: Unit) extends AnyVal {
    def apply(condition: AIn => Boolean): RouteAspect[Any, Nothing, AIn, AOut, AIn, AOut] =
      new RouteAspect[Any, Nothing, AIn, AOut, AIn, AOut] {
        override def apply[R1 <: Any, Err1 >: Nothing](route: Route[R1, Err1, AIn, AOut])(implicit
          trace: Trace,
        ): Route[R1, Err1, AIn, AOut] =
          route.when(condition)
      }
  }

  final class AllowZIO[AIn, AOut](val unit: Unit) extends AnyVal {
    def apply[R, Err](condition: AIn => ZIO[R, Err, Boolean]): RouteAspect[R, Err, AIn, AOut, AIn, AOut] =
      new RouteAspect[R, Err, AIn, AOut, AIn, AOut] {
        override def apply[R1 <: R, Err1 >: Err](route: Route[R1, Err1, AIn, AOut])(implicit
          trace: Trace,
        ): Route[R1, Err1, AIn, AOut] =
          route.whenZIO(condition)
      }
  }
}
