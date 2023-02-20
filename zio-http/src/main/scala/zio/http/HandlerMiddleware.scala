package zio.http
import zio.{Trace, ZIO}

import zio.stacktracer.TracingImplicits.disableAutoTrace // scalafix:ok;

trait HandlerMiddleware[+LowerEnv, -UpperEnv, +LowerErr, -UpperErr, +AIn, -AOut, -BIn <: AIn, +BOut]
    extends HandlerAspect[LowerEnv, UpperEnv, LowerErr, UpperErr, AIn, AOut, BIn, BOut]
    with Middleware[R, Err, AIn, AOut, BIn, BOut] { self =>

  final def >>>[R1 <: R, Err1 >: Err, AIn1 <: BIn, AOut1 >: BOut, BIn1 <: AIn1, BOut1](
    that: HandlerMiddleware[R1, Err1, AIn1, AOut1, BIn1, BOut1],
  ): HandlerMiddleware[R1, Err1, AIn, AOut, BIn1, BOut1] =
    self.andThen(that)

  final def ++[R1 <: R, Err1 >: Err, AIn1 <: BIn, AOut1 >: BOut, BIn1 <: AIn1, BOut1](
    that: HandlerMiddleware[R1, Err1, AIn1, AOut1, BIn1, BOut1],
  ): HandlerMiddleware[R1, Err1, AIn, AOut, BIn1, BOut1] =
    self.andThen(that)

  final def andThen[R1 <: R, Err1 >: Err, AIn1 <: BIn, AOut1 >: BOut, BIn1 <: AIn1, BOut1](
    that: HandlerMiddleware[R1, Err1, AIn1, AOut1, BIn1, BOut1],
  ): HandlerMiddleware[R1, Err1, AIn, AOut, BIn1, BOut1] =
    new HandlerMiddleware[R1, Err1, AIn, AOut, BIn1, BOut1] {
      override def apply[R2 <: R1, Err2 >: Err1](handler: Handler[R2, Err2, AIn, AOut])(implicit
        trace: Trace,
      ): Handler[R2, Err2, BIn1, BOut1] =
        that(self(handler))
    }

  override def apply[R1 <: R, Err1 >: Err](
    http: Http[R1, Err1, AIn, AOut],
  )(implicit trace: Trace): Http[R1, Err1, BIn, BOut] =
    http match {
      case Http.Empty                             => Http.empty
      case Http.Static(handler)                   => Http.Static(apply(handler))
      case route: Http.Route[R1, Err1, AIn, AOut] =>
        Http.fromHttpZIO { (in: BIn) =>
          route.run(in).map(_ @@ self)
        }
    }

  override def when[BIn1 <: BIn](
    condition: BIn1 => Boolean,
  )(implicit trace: Trace, ev: IsMono[AIn, AOut, BIn, BOut]): HandlerMiddleware[R, Err, AIn, AOut, BIn1, BOut] =
    new HandlerMiddleware[R, Err, AIn, AOut, BIn1, BOut] {
      override def apply[R1 <: R, Err1 >: Err](handler: Handler[R1, Err1, AIn, AOut])(implicit
        trace: Trace,
      ): Handler[R1, Err1, BIn1, BOut] =
        Handler.fromFunctionHandler[BIn1] { in =>
          if (condition(in)) self(handler)
          else handler.asInstanceOf[Handler[R1, Err1, BIn, BOut]]
        }
    }

  override def whenZIO[R1 <: R, Err1 >: Err, BIn1 <: BIn](
    condition: BIn1 => ZIO[R1, Err1, Boolean],
  )(implicit trace: Trace, ev: IsMono[AIn, AOut, BIn, BOut]): HandlerMiddleware[R1, Err1, AIn, AOut, BIn1, BOut] =
    new HandlerMiddleware[R1, Err1, AIn, AOut, BIn1, BOut] {
      override def apply[R2 <: R1, Err2 >: Err1](handler: Handler[R2, Err2, AIn, AOut])(implicit
        trace: Trace,
      ): Handler[R2, Err2, BIn1, BOut] =
        Handler
          .fromFunctionZIO[BIn1] { in =>
            condition(in).map {
              case true  => self(handler)
              case false => handler.asInstanceOf[Handler[R2, Err2, BIn, BOut]]
            }
          }
          .flatten
    }
}
