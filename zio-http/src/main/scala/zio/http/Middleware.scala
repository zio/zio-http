package zio.http

import zio.http.middleware.{HttpRoutesMiddlewares, RequestHandlerMiddlewares}
import zio.{Trace, ZIO}

import zio.stacktracer.TracingImplicits.disableAutoTrace // scalafix:ok;

object Middleware extends RequestHandlerMiddlewares with HttpRoutesMiddlewares {
  type WithOut[+LowerEnv, -UpperEnv, +LowerErr, -UpperErr, OutEnv0[_], OutErr0[_]] =
    Contextual[LowerEnv, UpperEnv, LowerErr, UpperErr] {
      type OutEnv[Env] = OutEnv0[Env]
      type OutErr[Err] = OutErr0[Err]
    }

  trait Contextual[+LowerEnv, -UpperEnv, +LowerErr, -UpperErr] {
    type OutEnv[Env]
    type OutErr[Err]

    def apply[Env >: LowerEnv <: UpperEnv, Err >: LowerErr <: UpperErr](
      http: Http[Env, Err, Request, Response],
    )(implicit trace: Trace): Http[OutEnv[Env], OutErr[Err], Request, Response]
  }

  trait Mono[+LowerEnv, -UpperEnv, +LowerErr, -UpperErr] extends Contextual[LowerEnv, UpperEnv, LowerErr, UpperErr] {
    final type OutEnv[Env] = Env
    final type OutErr[Err] = Err
  }

  /**
   * Creates a middleware which can allow or disallow access to an http based on
   * the predicate
   */
  def allow: Allow = new Allow(())

  /**
   * Creates a middleware which can allow or disallow access to an http based on
   * the predicate effect
   */
  def allowZIO: AllowZIO = new AllowZIO(())

  /**
   * An empty middleware that doesn't do perform any operations on the provided
   * Http and returns it as it is.
   */
  def identity: Middleware[Nothing, Any, Nothing, Any] =
    new Middleware.Mono[Nothing, Any, Nothing, Any] {
      override def apply[Env, Err](
        http: Http[Env, Err, Request, Response],
      )(implicit trace: Trace): Http[Env, Err, Request, Response] =
        http
    }

  final class Allow(val unit: Unit) extends AnyVal {
    def apply(condition: Request => Boolean): Middleware[Nothing, Any, Nothing, Any] =
      new Middleware.Mono[Nothing, Any, Nothing, Any] {
        override def apply[Env, Err](
          http: Http[Env, Err, Request, Response],
        )(implicit trace: Trace): Http[Env, Err, Request, Response] =
          http.when(condition)
      }
  }

  final class AllowZIO(val unit: Unit) extends AnyVal {
    def apply[R, Err](
      condition: Request => ZIO[R, Err, Boolean],
    ): Middleware[Nothing, R, Err, Any] =
      new Middleware.Mono[Nothing, R, Err, Any] {
        override def apply[Env <: R, Err1 >: Err](
          http: Http[Env, Err1, Request, Response],
        )(implicit trace: Trace): Http[Env, Err1, Request, Response] =
          http.whenZIO(condition)
      }
  }

  // TODO: if we have this, it gets picked up instead of HandlerMiddleware.MonoMethods
  /*
  implicit final class MonoMethods[R, Err, Request, Response, Request, Response](
    val self: Middleware.Mono[Nothing, R, Err, Any, Request, Response, Request, Response],
  ) extends AnyVal {

    /**
   * Applies Middleware based only if the condition function evaluates to true
   */
    def when[Request1 <: Request](
      condition: Request1 => Boolean,
    )(implicit
      trace: Trace,
      ev: IsMono[Request, Response, Request, Response],
    ): Middleware.Mono[Nothing, R, Err, Any, Request, Response, Request1, Response] =
      new Middleware[Nothing, R, Err, Any, Request, Response, Request1, Response] {
        override type OutEnv[Env1] = self.OutEnv[Env1]
        override type OutErr[Err1] = self.OutErr[Err1]

        override def apply[Env1 >: Nothing <: R, Err1 >: Err <: Any](http: Http[Env1, Err1, Request, Response])(implicit
          trace: Trace,
        ): Http[self.OutEnv[Env1], self.OutErr[Err1], Request1, Response] =
          http.when(condition).asInstanceOf[Http[self.OutEnv[Env1], self.OutErr[Err1], Request1, Response]]
      }

    /**
   * Applies Middleware based only if the condition effectful function
   * evaluates to true
   */
    def whenZIO[R1 <: R, Err1 >: Err, Request1 <: Request](
      condition: Request1 => ZIO[R1, Err1, Boolean],
    )(implicit
      trace: Trace,
      ev: IsMono[Request, Response, Request, Response],
    ): Middleware[Nothing, R1, Err1, Any, Request, Response, Request1, Response] =
      new Middleware[Nothing, R1, Err1, Any, Request, Response, Request1, Response] {
        override type OutEnv[Env2] = self.OutEnv[Env2]
        override type OutErr[Err2] = self.OutErr[Err2]

        override def apply[Env2 >: Nothing <: R1, Err2 >: Err1 <: Any](http: Http[Env2, Err2, Request, Response])(implicit
          trace: Trace,
        ): Http[OutEnv[Env2], OutErr[Err2], Request1, Response] =
          http.whenZIO(condition).asInstanceOf[Http[OutEnv[Env2], OutErr[Err2], Request1, Response]]
      }
  }
   */
}
