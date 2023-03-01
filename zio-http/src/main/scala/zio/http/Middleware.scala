package zio.http

import zio.http.middleware.{HttpRoutesMiddlewares, RequestHandlerMiddlewares}
import zio.{Trace, ZIO}

import zio.stacktracer.TracingImplicits.disableAutoTrace // scalafix:ok;

trait Middleware[+LowerEnv, -UpperEnv, +LowerErr, -UpperErr, +AIn, -AOut, -BIn, +BOut] {
  self =>
  type OutEnv[Env]
  type OutErr[Err]

  def apply[Env >: LowerEnv <: UpperEnv, Err >: LowerErr <: UpperErr](
    http: Http[Env, Err, AIn, AOut],
  )(implicit trace: Trace): Http[OutEnv[Env], OutErr[Err], BIn, BOut]

  final def applyToHttp[Env >: LowerEnv <: UpperEnv, Err >: LowerErr <: UpperErr](
    http: Http[Env, Err, AIn, AOut],
  )(implicit trace: Trace): Http[OutEnv[Env], OutErr[Err], BIn, BOut] =
    apply(http)
}

object Middleware extends RequestHandlerMiddlewares with HttpRoutesMiddlewares {
  type WithOut[+LowerEnv, -UpperEnv, +LowerErr, -UpperErr, +AIn, -AOut, -BIn, +BOut, OutEnv0[_], OutErr0[_]] =
    Middleware[LowerEnv, UpperEnv, LowerErr, UpperErr, AIn, AOut, BIn, BOut] {
      type OutEnv[Env] = OutEnv0[Env]
      type OutErr[Err] = OutErr0[Err]
    }

  type Mono[+LowerEnv, -UpperEnv, +LowerErr, -UpperErr, +AIn, -AOut, -BIn, +BOut] =
    Middleware[LowerEnv, UpperEnv, LowerErr, UpperErr, AIn, AOut, BIn, BOut] {
      type OutEnv[Env] = Env
      type OutErr[Err] = Err
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
  def identity[AIn, AOut]: Middleware[Nothing, Any, Nothing, Any, AIn, AOut, AIn, AOut] =
    new Middleware[Nothing, Any, Nothing, Any, AIn, AOut, AIn, AOut] {
      override type OutEnv[Env] = Env
      override type OutErr[Err] = Err

      override def apply[Env >: Nothing <: Any, Err >: Nothing <: Any](
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
    def apply(condition: AIn => Boolean): Middleware[Nothing, Any, Nothing, Any, AIn, AOut, AIn, AOut] =
      new Middleware[Nothing, Any, Nothing, Any, AIn, AOut, AIn, AOut] {
        override type OutEnv[Env] = Env
        override type OutErr[Err] = Err

        override def apply[Env >: Nothing <: Any, Err >: Nothing <: Any](
          http: Http[Env, Err, AIn, AOut],
        )(implicit trace: Trace): Http[Env, Err, AIn, AOut] =
          http.when(condition)
      }
  }

  final class AllowZIO[AIn, AOut](val unit: Unit) extends AnyVal {
    def apply[R, Err](
      condition: AIn => ZIO[R, Err, Boolean],
    ): Middleware[Nothing, R, Err, Any, AIn, AOut, AIn, AOut] =
      new Middleware[Nothing, R, Err, Any, AIn, AOut, AIn, AOut] {
        override type OutEnv[Env]  = Env
        override type OutErr[Err1] = Err1

        override def apply[Env >: Nothing <: R, Err1 >: Err <: Any](
          http: Http[Env, Err1, AIn, AOut],
        )(implicit trace: Trace): Http[Env, Err1, AIn, AOut] =
          http.whenZIO(condition)
      }
  }

  // TODO: if we have this, it gets picked up instead of HandlerMiddleware.MonoMethods
  /*
  implicit final class MonoMethods[R, Err, AIn, AOut, BIn, BOut](
    val self: Middleware.Mono[Nothing, R, Err, Any, AIn, AOut, BIn, BOut],
  ) extends AnyVal {

    /**
   * Applies Middleware based only if the condition function evaluates to true
   */
    def when[BIn1 <: BIn](
      condition: BIn1 => Boolean,
    )(implicit
      trace: Trace,
      ev: IsMono[AIn, AOut, BIn, BOut],
    ): Middleware.Mono[Nothing, R, Err, Any, AIn, AOut, BIn1, BOut] =
      new Middleware[Nothing, R, Err, Any, AIn, AOut, BIn1, BOut] {
        override type OutEnv[Env1] = self.OutEnv[Env1]
        override type OutErr[Err1] = self.OutErr[Err1]

        override def apply[Env1 >: Nothing <: R, Err1 >: Err <: Any](http: Http[Env1, Err1, AIn, AOut])(implicit
          trace: Trace,
        ): Http[self.OutEnv[Env1], self.OutErr[Err1], BIn1, BOut] =
          http.when(condition).asInstanceOf[Http[self.OutEnv[Env1], self.OutErr[Err1], BIn1, BOut]]
      }

    /**
   * Applies Middleware based only if the condition effectful function
   * evaluates to true
   */
    def whenZIO[R1 <: R, Err1 >: Err, BIn1 <: BIn](
      condition: BIn1 => ZIO[R1, Err1, Boolean],
    )(implicit
      trace: Trace,
      ev: IsMono[AIn, AOut, BIn, BOut],
    ): Middleware[Nothing, R1, Err1, Any, AIn, AOut, BIn1, BOut] =
      new Middleware[Nothing, R1, Err1, Any, AIn, AOut, BIn1, BOut] {
        override type OutEnv[Env2] = self.OutEnv[Env2]
        override type OutErr[Err2] = self.OutErr[Err2]

        override def apply[Env2 >: Nothing <: R1, Err2 >: Err1 <: Any](http: Http[Env2, Err2, AIn, AOut])(implicit
          trace: Trace,
        ): Http[OutEnv[Env2], OutErr[Err2], BIn1, BOut] =
          http.whenZIO(condition).asInstanceOf[Http[OutEnv[Env2], OutErr[Err2], BIn1, BOut]]
      }
  }
   */
}
