package zio.http

import zio.{Trace, ZIO}

import zio.stacktracer.TracingImplicits.disableAutoTrace // scalafix:ok;

trait HandlerAspect[+LowerEnv, -UpperEnv, +LowerErr, -UpperErr, +AIn, -AOut, -BIn, +BOut] { self =>
  type OutEnv[Env]
  type OutErr[Err]

  def apply[Env >: LowerEnv <: UpperEnv, Err >: LowerErr <: UpperErr](
    handler: Handler[Env, Err, AIn, AOut],
  )(implicit trace: Trace): Handler[OutEnv[Env], OutErr[Err], BIn, BOut]

  final def applyToHandler[Env >: LowerEnv <: UpperEnv, Err >: LowerErr <: UpperErr](
    handler: Handler[Env, Err, AIn, AOut],
  )(implicit trace: Trace): Handler[OutEnv[Env], OutErr[Err], BIn, BOut] =
    apply(handler)

  def toMiddleware[AIn1 >: AIn, BIn1 <: BIn](implicit
    ev: AIn1 <:< BIn1,
  ): HandlerMiddleware.WithOut[LowerEnv, UpperEnv, LowerErr, UpperErr, AIn1, AOut, AIn1, BOut, OutEnv, OutErr] =
    new HandlerMiddleware[LowerEnv, UpperEnv, LowerErr, UpperErr, AIn1, AOut, AIn1, BOut] {
      override type OutEnv[Env] = self.OutEnv[Env]
      override type OutErr[Err] = self.OutErr[Err]

      override def apply[Env >: LowerEnv <: UpperEnv, Err >: LowerErr <: UpperErr](
        handler: Handler[Env, Err, AIn1, AOut],
      )(implicit trace: Trace): Handler[OutEnv[Env], OutErr[Err], AIn1, BOut] =
        self(handler).contramap(ev.apply)
    }
}

object HandlerAspect {
  type WithOut[+LowerEnv, -UpperEnv, +LowerErr, -UpperErr, +AIn, -AOut, -BIn, +BOut, OutEnv0[_], OutErr0[_]] =
    HandlerAspect[LowerEnv, UpperEnv, LowerErr, UpperErr, AIn, AOut, BIn, BOut] {
      type OutEnv[Env] = OutEnv0[Env]
      type OutErr[Err] = OutErr0[Err]
    }
  type Mono[+LowerEnv, -UpperEnv, +LowerErr, -UpperErr, +AIn, -AOut, -BIn, +BOut]                            =
    HandlerAspect[LowerEnv, UpperEnv, LowerErr, UpperErr, AIn, AOut, BIn, BOut] {
      type OutEnv[Env] = Env
      type OutErr[Err] = Err
    }

  def codec[BIn, AOut]: Codec[BIn, AOut] = new Codec[BIn, AOut](())

  def codecHttp[BIn, AOut]: CodecHttp[BIn, AOut] = new CodecHttp[BIn, AOut](())

  def codecZIO[BIn, AOut]: CodecZIO[BIn, AOut] = new CodecZIO[BIn, AOut](())

  def identity[AIn, AOut]: HandlerMiddleware.Mono[Nothing, Any, Nothing, Any, AIn, AOut, AIn, AOut] =
    new HandlerMiddleware[Nothing, Any, Nothing, Any, AIn, AOut, AIn, AOut] {
      override type OutEnv[Env] = Env
      override type OutErr[Err] = Err

      override def apply[Env >: Nothing <: Any, Err >: Nothing <: Any](
        handler: Handler[Env, Err, AIn, AOut],
      )(implicit trace: Trace): Handler[Env, Err, AIn, AOut] =
        handler
    }

  def transform[BIn, AOut]: Transform[BIn, AOut] = new Transform[BIn, AOut](())

  final class Codec[BIn, AOut](val self: Unit) extends AnyVal {
    def apply[Err, AIn, BOut](
      decoder: BIn => Either[Err, AIn],
      encoder: AOut => Either[Err, BOut],
    ): HandlerAspect[Nothing, Any, Err, Any, AIn, AOut, BIn, BOut] =
      new HandlerAspect[Nothing, Any, Err, Any, AIn, AOut, BIn, BOut] {
        override type OutEnv[Env]  = Env
        override type OutErr[Err1] = Err1

        override def apply[Env >: Nothing <: Any, Err1 >: Err <: Any](
          handler: Handler[Env, Err1, AIn, AOut],
        )(implicit trace: Trace): Handler[Env, Err1, BIn, BOut] =
          handler
            .contramapZIO((in: BIn) => ZIO.fromEither(decoder(in)))
            .mapZIO(out => ZIO.fromEither(encoder(out)))
      }
  }

  final class CodecHttp[BIn, AOut](val self: Unit) extends AnyVal {
    def apply[R, Err, AIn, BOut](
      decoder: Handler[R, Err, BIn, AIn],
      encoder: Handler[R, Err, AOut, BOut],
    ): HandlerAspect.Mono[Nothing, R, Err, Any, AIn, AOut, BIn, BOut] =
      new HandlerAspect[Nothing, R, Err, Any, AIn, AOut, BIn, BOut] {
        override type OutEnv[Env1] = Env1
        override type OutErr[Err1] = Err1

        override def apply[Env >: Nothing <: R, Err1 >: Err <: Any](handler: Handler[Env, Err1, AIn, AOut])(implicit
          trace: Trace,
        ): Handler[Env, Err1, BIn, BOut] =
          decoder >>> handler >>> encoder
      }
  }

  final class CodecZIO[BIn, AOut](val self: Unit) extends AnyVal {
    def apply[R, Err, AIn, BOut](
      decoder: BIn => ZIO[R, Err, AIn],
      encoder: AOut => ZIO[R, Err, BOut],
    ): HandlerAspect.Mono[Nothing, R, Err, Any, AIn, AOut, BIn, BOut] =
      new HandlerAspect[Nothing, R, Err, Any, AIn, AOut, BIn, BOut] {
        override type OutEnv[Env1] = Env1
        override type OutErr[Err1] = Err1

        override def apply[R1 <: R, Err1 >: Err](
          handler: Handler[R1, Err1, AIn, AOut],
        )(implicit trace: Trace): Handler[R1, Err1, BIn, BOut] =
          handler
            .contramapZIO((in: BIn) => decoder(in))
            .mapZIO(out => encoder(out))
      }
  }

  final class Transform[BIn, AOut](val self: Unit) extends AnyVal {
    def apply[AIn, BOut](
      in: BIn => AIn,
      out: AOut => BOut,
    ): HandlerAspect.Mono[Nothing, Any, Nothing, Any, AIn, AOut, BIn, BOut] =
      new HandlerAspect[Nothing, Any, Nothing, Any, AIn, AOut, BIn, BOut] {
        override type OutEnv[Env] = Env
        override type OutErr[Err] = Err

        override def apply[R1 <: Any, Err1 >: Nothing](handler: Handler[R1, Err1, AIn, AOut])(implicit
          trace: Trace,
        ): Handler[R1, Err1, BIn, BOut] =
          handler.contramap(in).map(out)
      }
  }
}
