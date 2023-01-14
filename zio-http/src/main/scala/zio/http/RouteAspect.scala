package zio.http

import zio.{Trace, ZIO}

trait RouteAspect[-R, +Err, +AIn, -AOut, -BIn, +BOut] { self =>
  final def >>>[R1 <: R, Err1 >: Err, AIn1 <: BIn, AOut1 >: BOut, BIn1, BOut1](
    that: RouteAspect[R1, Err1, AIn1, AOut1, BIn1, BOut1],
  ): RouteAspect[R1, Err1, AIn, AOut, BIn1, BOut1] =
    self.andThen(that)

  final def ++[R1 <: R, Err1 >: Err, AIn1 <: BIn, AOut1 >: BOut, BIn1, BOut1](
    that: RouteAspect[R1, Err1, AIn1, AOut1, BIn1, BOut1],
  ): RouteAspect[R1, Err1, AIn, AOut, BIn1, BOut1] =
    self.andThen(that)

  final def andThen[R1 <: R, Err1 >: Err, AIn1 <: BIn, AOut1 >: BOut, BIn1, BOut1](
    that: RouteAspect[R1, Err1, AIn1, AOut1, BIn1, BOut1],
  ): RouteAspect[R1, Err1, AIn, AOut, BIn1, BOut1] =
    new RouteAspect[R1, Err1, AIn, AOut, BIn1, BOut1] {
      override def apply[R2 <: R1, Err2 >: Err1](handler: Http[R2, Err2, AIn, AOut])(implicit
        trace: Trace,
      ): Http[R2, Err2, BIn1, BOut1] =
        that(self(handler))
    }

  def apply[R1 <: R, Err1 >: Err](route: Http[R1, Err1, AIn, AOut])(implicit
    trace: Trace,
  ): Http[R1, Err1, BIn, BOut]

  def when[BIn1 <: BIn](
    condition: BIn1 => Boolean,
  )(implicit trace: Trace, ev: IsMono[AIn, AOut, BIn, BOut]): RouteAspect[R, Err, AIn, AOut, BIn1, BOut] =
    new RouteAspect[R, Err, AIn, AOut, BIn1, BOut] {
      override def apply[R1 <: R, Err1 >: Err](route: Http[R1, Err1, AIn, AOut])(implicit
        trace: Trace,
      ): Http[R1, Err1, BIn1, BOut] =
        route.when(condition).asInstanceOf[Http[R1, Err1, BIn1, BOut]]
    }

  def whenZIO[R1 <: R, Err1 >: Err, BIn1 <: BIn](
    condition: BIn1 => ZIO[R1, Err1, Boolean],
  )(implicit trace: Trace, ev: IsMono[AIn, AOut, BIn, BOut]): RouteAspect[R1, Err1, AIn, AOut, BIn1, BOut] =
    new RouteAspect[R1, Err1, AIn, AOut, BIn1, BOut] {
      override def apply[R2 <: R1, Err2 >: Err1](route: Http[R2, Err2, AIn, AOut])(implicit
        trace: Trace,
      ): Http[R2, Err2, BIn1, BOut] =
        route.whenZIO(condition).asInstanceOf[Http[R2, Err2, BIn1, BOut]]

    }
}

object RouteAspect {

  def allow[AIn, AOut]: Allow[AIn, AOut] = new Allow[AIn, AOut](())

  def allowZIO[AIn, AOut]: AllowZIO[AIn, AOut] = new AllowZIO[AIn, AOut](())

  def identity[AIn, AOut]: RouteAspect[Any, Nothing, AIn, AOut, AIn, AOut] =
    new RouteAspect[Any, Nothing, AIn, AOut, AIn, AOut] {
      override def apply[R1 <: Any, Err1 >: Nothing](route: Http[R1, Err1, AIn, AOut])(implicit
        trace: Trace,
      ): Http[R1, Err1, AIn, AOut] =
        route
    }

  final class Allow[AIn, AOut](val unit: Unit) extends AnyVal {
    def apply(condition: AIn => Boolean): RouteAspect[Any, Nothing, AIn, AOut, AIn, AOut] =
      new RouteAspect[Any, Nothing, AIn, AOut, AIn, AOut] {
        override def apply[R1 <: Any, Err1 >: Nothing](route: Http[R1, Err1, AIn, AOut])(implicit
          trace: Trace,
        ): Http[R1, Err1, AIn, AOut] =
          route.when(condition)
      }
  }

  final class AllowZIO[AIn, AOut](val unit: Unit) extends AnyVal {
    def apply[R, Err](condition: AIn => ZIO[R, Err, Boolean]): RouteAspect[R, Err, AIn, AOut, AIn, AOut] =
      new RouteAspect[R, Err, AIn, AOut, AIn, AOut] {
        override def apply[R1 <: R, Err1 >: Err](route: Http[R1, Err1, AIn, AOut])(implicit
          trace: Trace,
        ): Http[R1, Err1, AIn, AOut] =
          route.whenZIO(condition)
      }
  }
}
