package zio.http
import zio.{Trace, ZIO}

import zio.stacktracer.TracingImplicits.disableAutoTrace // scalafix:ok;

trait HandlerMiddleware[+LowerEnv, -UpperEnv, +LowerErr, -UpperErr, +AIn, -AOut, -BIn <: AIn, +BOut]
    extends HandlerAspect[LowerEnv, UpperEnv, LowerErr, UpperErr, AIn, AOut, BIn, BOut]
    with Middleware[LowerEnv, UpperEnv, LowerErr, UpperErr, AIn, AOut, BIn, BOut] { self =>

  final def >>>[LowerEnv2, UpperEnv2, LowerErr2, UpperErr2, AIn1 <: BIn, AOut1 >: BOut, BIn1 <: AIn1, BOut1](
    that: HandlerMiddleware[LowerEnv2, UpperEnv2, LowerErr2, UpperErr2, AIn1, AOut1, BIn1, BOut1],
  )(implicit
    composeEnv: ZCompose[LowerEnv, UpperEnv, OutEnv, LowerEnv2, UpperEnv2, that.OutEnv],
    composeErr: ZCompose[LowerErr, UpperErr, OutErr, LowerErr2, UpperErr2, that.OutErr],
  ): HandlerMiddleware.WithOut[
    composeEnv.Lower,
    composeEnv.Upper,
    composeErr.Lower,
    composeErr.Upper,
    AIn,
    AOut,
    BIn1,
    BOut1,
    composeEnv.Out,
    composeErr.Out,
  ] =
    self.andThen(that)

  final def ++[LowerEnv2, UpperEnv2, LowerErr2, UpperErr2, AIn1 <: BIn, AOut1 >: BOut, BIn1 <: AIn1, BOut1](
    that: HandlerMiddleware[LowerEnv2, UpperEnv2, LowerErr2, UpperErr2, AIn1, AOut1, BIn1, BOut1],
  )(implicit
    composeEnv: ZCompose[LowerEnv, UpperEnv, OutEnv, LowerEnv2, UpperEnv2, that.OutEnv],
    composeErr: ZCompose[LowerErr, UpperErr, OutErr, LowerErr2, UpperErr2, that.OutErr],
  ): HandlerMiddleware.WithOut[
    composeEnv.Lower,
    composeEnv.Upper,
    composeErr.Lower,
    composeErr.Upper,
    AIn,
    AOut,
    BIn1,
    BOut1,
    composeEnv.Out,
    composeErr.Out,
  ] =
    self.andThen(that)

  final def andThen[LowerEnv2, UpperEnv2, LowerErr2, UpperErr2, AIn1 <: BIn, AOut1 >: BOut, BIn1 <: AIn1, BOut1](
    that: HandlerMiddleware[LowerEnv2, UpperEnv2, LowerErr2, UpperErr2, AIn1, AOut1, BIn1, BOut1],
  )(implicit
    composeEnv: ZCompose[LowerEnv, UpperEnv, OutEnv, LowerEnv2, UpperEnv2, that.OutEnv],
    composeErr: ZCompose[LowerErr, UpperErr, OutErr, LowerErr2, UpperErr2, that.OutErr],
  ): HandlerMiddleware.WithOut[
    composeEnv.Lower,
    composeEnv.Upper,
    composeErr.Lower,
    composeErr.Upper,
    AIn,
    AOut,
    BIn1,
    BOut1,
    composeEnv.Out,
    composeErr.Out,
  ] =
    new HandlerMiddleware[
      composeEnv.Lower,
      composeEnv.Upper,
      composeErr.Lower,
      composeErr.Upper,
      AIn,
      AOut,
      BIn1,
      BOut1,
    ] {
      override type OutEnv[Env] = composeEnv.Out[Env]
      override type OutErr[Err] = composeErr.Out[Err]

      override def apply[Env >: composeEnv.Lower <: composeEnv.Upper, Err >: composeErr.Lower <: composeErr.Upper](
        handler: Handler[Env, Err, AIn, AOut],
      )(implicit trace: Trace): Handler[OutEnv[Env], OutErr[Err], BIn1, BOut1] = {
        val h0 = handler.asInstanceOf[Handler[Nothing, Any, AIn, AOut]]
        val h1 = self.asInstanceOf[HandlerMiddleware[Nothing, Any, Nothing, Any, AIn, AOut, BIn, BOut]].applyToHandler(handler)
        val h2 = that.asInstanceOf[HandlerMiddleware[Nothing, Any, Nothing, Any, AIn, AOut, BIn, BOut]].applyToHandler(h1)
        h2.asInstanceOf[Handler[OutEnv[Env], OutErr[Err], BIn1, BOut1]]
      }
    }

  override def apply[Env >: LowerEnv <: UpperEnv, Err >: LowerErr <: UpperErr](
    http: Http[Env, Err, AIn, AOut],
  ): Http[OutEnv[Env], OutErr[Err], BIn, BOut] =
    http match {
      case Http.Empty                             => Http.empty
      case Http.Static(handler)                   => Http.Static(apply(handler))
      case route: Http.Route[Env, Err, AIn, AOut] =>
        Http.fromHttpZIO { (in: BIn) =>
          route.run(in).map(_ @@ self)
        }
    }

//  override def apply[R1 <: R, Err1 >: Err](
//    http: Http[R1, Err1, AIn, AOut],
//  )(implicit trace: Trace): Http[R1, Err1, BIn, BOut] =
//    http match {
//      case Http.Empty                             => Http.empty
//      case Http.Static(handler)                   => Http.Static(apply(handler))
//      case route: Http.Route[R1, Err1, AIn, AOut] =>
//        Http.fromHttpZIO { (in: BIn) =>
//          route.run(in).map(_ @@ self)
//        }
//    }

//  override def when[BIn1 <: BIn](
//    condition: BIn1 => Boolean,
//  )(implicit trace: Trace, ev: IsMono[AIn, AOut, BIn, BOut]): HandlerMiddleware[R, Err, AIn, AOut, BIn1, BOut] =
//    new HandlerMiddleware[R, Err, AIn, AOut, BIn1, BOut] {
//      override def apply[R1 <: R, Err1 >: Err](handler: Handler[R1, Err1, AIn, AOut])(implicit
//        trace: Trace,
//      ): Handler[R1, Err1, BIn1, BOut] =
//        Handler.fromFunctionHandler[BIn1] { in =>
//          if (condition(in)) self(handler)
//          else handler.asInstanceOf[Handler[R1, Err1, BIn, BOut]]
//        }
//    }
//
//  override def whenZIO[R1 <: R, Err1 >: Err, BIn1 <: BIn](
//    condition: BIn1 => ZIO[R1, Err1, Boolean],
//  )(implicit trace: Trace, ev: IsMono[AIn, AOut, BIn, BOut]): HandlerMiddleware[R1, Err1, AIn, AOut, BIn1, BOut] =
//    new HandlerMiddleware[R1, Err1, AIn, AOut, BIn1, BOut] {
//      override def apply[R2 <: R1, Err2 >: Err1](handler: Handler[R2, Err2, AIn, AOut])(implicit
//        trace: Trace,
//      ): Handler[R2, Err2, BIn1, BOut] =
//        Handler
//          .fromFunctionZIO[BIn1] { in =>
//            condition(in).map {
//              case true  => self(handler)
//              case false => handler.asInstanceOf[Handler[R2, Err2, BIn, BOut]]
//            }
//          }
//          .flatten
//    }
}

object HandlerMiddleware {
  type WithOut[+LowerEnv, -UpperEnv, +LowerErr, -UpperErr, +AIn, -AOut, -BIn <: AIn, +BOut, OutEnv0[_], OutErr0[_]] =
    HandlerMiddleware[LowerEnv, UpperEnv, LowerErr, UpperErr, AIn, AOut, BIn, BOut] {
      type OutEnv[Env] = OutEnv0[Env]
      type OutErr[Err] = OutErr0[Err]
    }
}
