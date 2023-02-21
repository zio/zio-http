package zio.http

import zio.http.middleware.{HttpRoutesMiddlewares, RequestHandlerMiddlewares}
import zio.{Trace, ZIO}

import zio.stacktracer.TracingImplicits.disableAutoTrace // scalafix:ok;

trait Middleware[+LowerEnv, -UpperEnv, +LowerErr, -UpperErr, +AIn, -AOut, -BIn, +BOut] { self =>
  type OutEnv[Env]
  type OutErr[Err]

  def apply[Env >: LowerEnv <: UpperEnv, Err >: LowerErr <: UpperErr](
    http: Http[Env, Err, AIn, AOut],
  )(implicit trace: Trace): Http[OutEnv[Env], OutErr[Err], BIn, BOut]

  final def applyToHttp[Env >: LowerEnv <: UpperEnv, Err >: LowerErr <: UpperErr](
    http: Http[Env, Err, AIn, AOut],
  )(implicit trace: Trace): Http[OutEnv[Env], OutErr[Err], BIn, BOut] =
    apply(http)

  /**
   * Applies Middleware based only if the condition function evaluates to true
   */
  def when[BIn1 <: BIn](
    condition: BIn1 => Boolean,
  )(implicit
    trace: Trace,
    ev: IsMono[AIn, AOut, BIn, BOut],
  ): Middleware[LowerEnv, UpperEnv, LowerErr, UpperErr, AIn, AOut, BIn1, BOut] =
    new Middleware[LowerEnv, UpperEnv, LowerErr, UpperErr, AIn, AOut, BIn1, BOut] {
      override type OutEnv[Env] = self.OutEnv[Env]
      override type OutErr[Err] = self.OutErr[Err]

      override def apply[Env >: LowerEnv <: UpperEnv, Err >: LowerErr <: UpperErr](http: Http[Env, Err, AIn, AOut])(
        implicit trace: Trace,
      ): Http[self.OutEnv[Env], self.OutErr[Err], BIn1, BOut] =
        http.when(condition).asInstanceOf[Http[self.OutEnv[Env], self.OutErr[Err], BIn1, BOut]]
    }

  /**
   * Applies Middleware based only if the condition effectful function evaluates
   * to true
   */
  def whenZIO[R1 <: UpperEnv, Err1 >: LowerEnv, BIn1 <: BIn](
    condition: BIn1 => ZIO[R1, Err1, Boolean],
  )(implicit
    trace: Trace,
    ev: IsMono[AIn, AOut, BIn, BOut],
  ): Middleware[LowerEnv, R1, Err1, UpperErr, AIn, AOut, BIn1, BOut] =
    new Middleware[LowerEnv, R1, Err1, UpperErr, AIn, AOut, BIn1, BOut] {
      override type OutEnv[Env] = self.OutEnv[Env]
      override type OutErr[Err] = self.OutErr[Err]

      override def apply[Env >: LowerEnv <: R1, Err >: Err1 <: UpperErr](
        http: Http[Env, Err, AIn, AOut],
      )(implicit trace: Trace): Http[Env, Err, BIn1, BOut] =
        http.whenZIO(condition).asInstanceOf[Http[Env, Err, BIn1, BOut]]
    }
}

object Middleware extends RequestHandlerMiddlewares with HttpRoutesMiddlewares {
  type WithOut[+LowerEnv, -UpperEnv, +LowerErr, -UpperErr, +AIn, -AOut, -BIn, +BOut, OutEnv0[_], OutErr0[_]] =
    Middleware[LowerEnv, UpperEnv, LowerErr, UpperErr, AIn, AOut, BIn, BOut] {
      type OutEnv[Env] = OutEnv0[Env]
      type OutErr[Err] = OutErr0[Err]
    }

  /**
   * Creates a middleware which can allow or disallow access to an http based on
   * the predicate
   */
  def allow[AIn, AOut]: Allow[AIn, AOut] = new Allow[AIn, AOut](())

  /**
   * Creates a middleware which can allow or disallow access to an http based on
   * the predicate effect
   */
  def allowZIO[AIn, AOut]: AllowZIO[AIn, AOut] = new AllowZIO[AIn, AOut](())

  /**
   * Creates a middleware using the specified encoder and decoder functions
   */
  def codec[BIn, AOut]: HandlerAspect.Codec[BIn, AOut] = HandlerAspect.codec[BIn, AOut]

  /**
   * Creates a codec middleware using two Http.
   */
  def codecHttp[BIn, AOut]: HandlerAspect.CodecHttp[BIn, AOut] = HandlerAspect.codecHttp[BIn, AOut]

  /**
   * Creates a middleware using specified effectful encoder and decoder
   */
  def codecZIO[BIn, AOut]: HandlerAspect.CodecZIO[BIn, AOut] = HandlerAspect.codecZIO[BIn, AOut]

  /**
   * An empty middleware that doesn't do perform any operations on the provided
   * Http and returns it as it is.
   */
  def identity[AIn, AOut]: Middleware[Nothing, Any, Any, Nothing, AIn, AOut, AIn, AOut] =
    new Middleware[Nothing, Any, Any, Nothing, AIn, AOut, AIn, AOut] {
      override type OutEnv[Env] = Env
      override type OutErr[Err] = Err

      override def apply[Env >: Nothing <: Any, Err >: Any <: Nothing](
        http: Http[Env, Err, AIn, AOut],
      )(implicit trace: Trace): Http[Env, Err, AIn, AOut] =
        http
    }

  /**
   * Creates a new middleware using two transformation functions, one that's
   * applied to the incoming type of the Http and one that applied to the
   * outgoing type of the Http.
   */
  def transform[BIn, AOut]: HandlerAspect.Transform[BIn, AOut] = HandlerAspect.transform[BIn, AOut]

  final class Allow[AIn, AOut](val unit: Unit) extends AnyVal {
    def apply(condition: AIn => Boolean): Middleware[Nothing, Any, Any, Nothing, AIn, AOut, AIn, AOut] =
      new Middleware[Nothing, Any, Any, Nothing, AIn, AOut, AIn, AOut] {
        override type OutEnv[Env] = Env
        override type OutErr[Err] = Err

        override def apply[Env >: Nothing <: Any, Err >: Any <: Nothing](
          http: Http[Env, Err, AIn, AOut],
        )(implicit trace: Trace): Http[Env, Err, AIn, AOut] =
          http.when(condition)
      }
  }

  final class AllowZIO[AIn, AOut](val unit: Unit) extends AnyVal {
    def apply[R, Err](
      condition: AIn => ZIO[R, Err, Boolean],
    ): Middleware[Nothing, R, Err, Nothing, AIn, AOut, AIn, AOut] =
      new Middleware[Nothing, R, Err, Nothing, AIn, AOut, AIn, AOut] {
        override type OutEnv[Env]  = Env
        override type OutErr[Err1] = Err1

        override def apply[Env >: Nothing <: R, Err1 >: Err <: Nothing](
          http: Http[Env, Err1, AIn, AOut],
        )(implicit trace: Trace): Http[Env, Err, AIn, AOut] =
          http.whenZIO(condition)
      }
  }
}
