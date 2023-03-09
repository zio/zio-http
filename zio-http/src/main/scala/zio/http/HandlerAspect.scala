package zio.http

import zio.{Trace, ZIO}

import zio.stacktracer.TracingImplicits.disableAutoTrace // scalafix:ok;

trait HandlerAspect[+LowerEnv, -UpperEnv, +LowerErr, -UpperErr] { self =>
  type OutEnv[Env]
  type OutErr[Err]

  def apply[Env >: LowerEnv <: UpperEnv, Err >: LowerErr <: UpperErr](
    handler: Handler[Env, Err, Request, Response],
  )(implicit trace: Trace): Handler[OutEnv[Env], OutErr[Err], Request, Response]

  final def applyToHandler[Env >: LowerEnv <: UpperEnv, Err >: LowerErr <: UpperErr](
    handler: Handler[Env, Err, Request, Response],
  )(implicit trace: Trace): Handler[OutEnv[Env], OutErr[Err], Request, Response] =
    apply(handler)

  def toMiddleware: HandlerMiddleware.WithOut[LowerEnv, UpperEnv, LowerErr, UpperErr, OutEnv, OutErr] =
    new HandlerMiddleware[LowerEnv, UpperEnv, LowerErr, UpperErr] {
      override type OutEnv[Env] = self.OutEnv[Env]
      override type OutErr[Err] = self.OutErr[Err]

      override def apply[Env >: LowerEnv <: UpperEnv, Err >: LowerErr <: UpperErr](
        handler: Handler[Env, Err, Request, Response],
      )(implicit trace: Trace): Handler[OutEnv[Env], OutErr[Err], Request, Response] =
        self(handler)
    }
}

object HandlerAspect {
  type WithOut[+LowerEnv, -UpperEnv, +LowerErr, -UpperErr, OutEnv0[_], OutErr0[_]] =
    HandlerAspect[LowerEnv, UpperEnv, LowerErr, UpperErr] {
      type OutEnv[Env] = OutEnv0[Env]
      type OutErr[Err] = OutErr0[Err]
    }
  type Mono[+LowerEnv, -UpperEnv, +LowerErr, -UpperErr]                            =
    HandlerAspect[LowerEnv, UpperEnv, LowerErr, UpperErr] {
      type OutEnv[Env] = Env
      type OutErr[Err] = Err
    }

  def identity[AIn, AOut]: HandlerMiddleware.Mono[Nothing, Any, Nothing, Any] =
    new HandlerMiddleware[Nothing, Any, Nothing, Any] {
      override type OutEnv[Env] = Env
      override type OutErr[Err] = Err

      override def apply[Env >: Nothing <: Any, Err >: Nothing <: Any](
        handler: Handler[Env, Err, Request, Response],
      )(implicit trace: Trace): Handler[Env, Err, Request, Response] =
        handler
    }
}
