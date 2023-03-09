package zio.http
import zio.{Trace, ZIO}
import zio.stacktracer.TracingImplicits.disableAutoTrace

import scala.annotation.nowarn
import scala.annotation.unchecked.uncheckedVariance // scalafix:ok;

trait HandlerMiddleware[+LowerEnv, -UpperEnv, +LowerErr, -UpperErr]
    extends HandlerAspect[LowerEnv, UpperEnv, LowerErr, UpperErr]
    with Middleware[LowerEnv, UpperEnv, LowerErr, UpperErr] { self =>

  final def >>>[LowerEnv2, UpperEnv2, LowerErr2, UpperErr2](
    that: HandlerMiddleware[LowerEnv2, UpperEnv2, LowerErr2, UpperErr2],
  )(implicit
    composeEnv: ZCompose[LowerEnv, UpperEnv, OutEnv, LowerEnv2, UpperEnv2, that.OutEnv] @uncheckedVariance,
    composeErr: ZCompose[LowerErr, UpperErr, OutErr, LowerErr2, UpperErr2, that.OutErr] @uncheckedVariance,
  ): HandlerMiddleware.WithOut[
    composeEnv.Lower,
    composeEnv.Upper,
    composeErr.Lower,
    composeErr.Upper,
    composeEnv.Out,
    composeErr.Out,
  ] =
    self.andThen(that)

  final def ++[
    LowerEnv2,
    UpperEnv2,
    LowerErr2,
    UpperErr2,
  ](
    that: HandlerMiddleware[LowerEnv2, UpperEnv2, LowerErr2, UpperErr2],
  )(implicit
    composeEnv: ZCompose[LowerEnv, UpperEnv, OutEnv, LowerEnv2, UpperEnv2, that.OutEnv] @uncheckedVariance,
    composeErr: ZCompose[LowerErr, UpperErr, OutErr, LowerErr2, UpperErr2, that.OutErr] @uncheckedVariance,
  ): HandlerMiddleware.WithOut[
    composeEnv.Lower,
    composeEnv.Upper,
    composeErr.Lower,
    composeErr.Upper,
    composeEnv.Out,
    composeErr.Out,
  ] =
    self.andThen(that)

  final def andThen[LowerEnv2, UpperEnv2, LowerErr2, UpperErr2](
    that: HandlerMiddleware[LowerEnv2, UpperEnv2, LowerErr2, UpperErr2],
  )(implicit
    composeEnv: ZCompose[LowerEnv, UpperEnv, OutEnv, LowerEnv2, UpperEnv2, that.OutEnv] @uncheckedVariance,
    composeErr: ZCompose[LowerErr, UpperErr, OutErr, LowerErr2, UpperErr2, that.OutErr] @uncheckedVariance,
  ): HandlerMiddleware.WithOut[
    composeEnv.Lower,
    composeEnv.Upper,
    composeErr.Lower,
    composeErr.Upper,
    composeEnv.Out,
    composeErr.Out,
  ] =
    new HandlerMiddleware[
      composeEnv.Lower,
      composeEnv.Upper,
      composeErr.Lower,
      composeErr.Upper,
    ] {
      override type OutEnv[Env] = composeEnv.Out[Env]
      override type OutErr[Err] = composeErr.Out[Err]

      override def apply[Env >: composeEnv.Lower <: composeEnv.Upper, Err >: composeErr.Lower <: composeErr.Upper](
        handler: Handler[Env, Err, Request, Response],
      )(implicit trace: Trace): Handler[OutEnv[Env], OutErr[Err], Request, Response] = {
        val h1 =
          self.asInstanceOf[HandlerMiddleware[Nothing, Any, Nothing, Any]].applyToHandler(handler)
        val h2 = that
          .asInstanceOf[HandlerMiddleware[Nothing, Any, Nothing, Any]]
          .applyToHandler(h1)
        h2.asInstanceOf[Handler[OutEnv[Env], OutErr[Err], Request, Response]]
      }
    }

  override def apply[Env >: LowerEnv <: UpperEnv, Err >: LowerErr <: UpperErr](
    http: Http[Env, Err, Request, Response],
  )(implicit trace: Trace): Http[OutEnv[Env], OutErr[Err], Request, Response] =
    http match {
      case Http.Empty                                     => Http.empty
      case Http.Static(handler)                           => Http.Static(apply(handler))
      case route: Http.Route[Env, Err, Request, Response] =>
        Http.fromHttpZIO[Request] { in =>
          route
            .run(in)
            .map { (http: Http[Env, Err, Request, Response]) => http @@ self }
            .asInstanceOf[ZIO[OutEnv[Env], OutErr[Err], Http[OutEnv[Env], OutErr[
              Err,
            ], Request, Response]]] // TODO: can we avoid this?
        }: @nowarn // TODO: why is this reported as unreachable code?a
    }
}

object HandlerMiddleware {
  type WithOut[+LowerEnv, -UpperEnv, +LowerErr, -UpperErr, OutEnv0[_], OutErr0[_]] =
    HandlerMiddleware[LowerEnv, UpperEnv, LowerErr, UpperErr] {
      type OutEnv[Env] = OutEnv0[Env]
      type OutErr[Err] = OutErr0[Err]
    }
  type Mono[+LowerEnv, -UpperEnv, +LowerErr, -UpperErr]                            =
    HandlerMiddleware[LowerEnv, UpperEnv, LowerErr, UpperErr] {
      type OutEnv[Env] = Env
      type OutErr[Err] = Err
    }

  implicit final class MonoMethods[R, Err](
    val self: HandlerMiddleware.Mono[Nothing, R, Err, Any],
  ) extends AnyVal {
    def when(
      condition: Request => Boolean,
    )(implicit
      trace: Trace,
    ): HandlerMiddleware.Mono[Nothing, R, Err, Any] =
      new HandlerMiddleware[Nothing, R, Err, Any] {
        override type OutEnv[Env1] = Env1
        override type OutErr[Err1] = Err1

        override def apply[R1 <: R, Err1 >: Err](handler: Handler[R1, Err1, Request, Response])(implicit
          trace: Trace,
        ): Handler[R1, Err1, Request, Response] =
          Handler.fromFunctionHandler[Request].apply[R1, Err1, Response] { in =>
            if (condition(in)) {
              val a = self.apply(handler)
              a
            } else handler.asInstanceOf[Handler[R1, Err1, Request, Response]]
          }
      }

    def whenZIO[R1 <: R, Err1 >: Err](
      condition: Request => ZIO[R1, Err1, Boolean],
    )(implicit
      trace: Trace,
      ev: IsMono[Request, Response, Request, Response],
    ): HandlerMiddleware.Mono[Nothing, R1, Err1, Any] =
      new HandlerMiddleware[Nothing, R1, Err1, Any] {
        override type OutEnv[Env2] = Env2
        override type OutErr[Err2] = Err2

        override def apply[R2 <: R1, Err2 >: Err1](handler: Handler[R2, Err2, Request, Response])(implicit
          trace: Trace,
        ): Handler[R2, Err2, Request, Response] =
          Handler
            .fromFunctionZIO[Request] { in =>
              condition(in).map {
                case true  => self(handler)
                case false => handler
              }
            }
            .flatten
      }
  }
}
