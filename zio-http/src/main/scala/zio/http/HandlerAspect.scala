package zio.http

import zio.Trace

import zio.stacktracer.TracingImplicits.disableAutoTrace // scalafix:ok;

object HandlerAspect {
  type WithOut[+LowerEnv, -UpperEnv, +LowerErr, -UpperErr, OutEnv0[_], OutErr0[_]] =
    Contextual[LowerEnv, UpperEnv, LowerErr, UpperErr] {
      type OutEnv[Env] = OutEnv0[Env]
      type OutErr[Err] = OutErr0[Err]
    }

  trait Contextual[+LowerEnv, -UpperEnv, +LowerErr, -UpperErr] {
    self =>
    type OutEnv[Env]
    type OutErr[Err]

    def apply[Env >: LowerEnv <: UpperEnv, Err >: LowerErr <: UpperErr](
      handler: Handler[Env, Err, Request, Response],
    )(implicit trace: Trace): Handler[OutEnv[Env], OutErr[Err], Request, Response]

    def toMiddleware: HandlerMiddleware.WithOut[LowerEnv, UpperEnv, LowerErr, UpperErr, OutEnv, OutErr] =
      new HandlerMiddleware.Contextual[LowerEnv, UpperEnv, LowerErr, UpperErr] {
        override type OutEnv[Env] = self.OutEnv[Env]
        override type OutErr[Err] = self.OutErr[Err]

        override def apply[Env >: LowerEnv <: UpperEnv, Err >: LowerErr <: UpperErr](
          handler: Handler[Env, Err, Request, Response],
        )(implicit trace: Trace): Handler[OutEnv[Env], OutErr[Err], Request, Response] =
          self(handler)
      }
  }

  trait Simple[+LowerEnv, -UpperEnv, +LowerErr, -UpperErr] extends Contextual[LowerEnv, UpperEnv, LowerErr, UpperErr] {
    self =>
    final type OutEnv[Env] = Env
    final type OutErr[Err] = Err

    def apply[Env >: LowerEnv <: UpperEnv, Err >: LowerErr <: UpperErr](
      handler: Handler[Env, Err, Request, Response],
    )(implicit trace: Trace): Handler[Env, Err, Request, Response]

    override def toMiddleware: HandlerMiddleware.WithOut[LowerEnv, UpperEnv, LowerErr, UpperErr, OutEnv, OutErr] =
      new HandlerMiddleware.Simple[LowerEnv, UpperEnv, LowerErr, UpperErr] {
        override def apply[Env >: LowerEnv <: UpperEnv, Err >: LowerErr <: UpperErr](
          handler: Handler[Env, Err, Request, Response],
        )(implicit trace: Trace): Handler[Env, Err, Request, Response] =
          self(handler)
      }
  }

  def identity[AIn, AOut]: HandlerAspect[Nothing, Any, Nothing, Any] =
    new HandlerAspect.Simple[Nothing, Any, Nothing, Any] {
      override def apply[Env >: Nothing <: Any, Err >: Nothing <: Any](
        handler: Handler[Env, Err, Request, Response],
      )(implicit trace: Trace): Handler[Env, Err, Request, Response] =
        handler
    }
}
