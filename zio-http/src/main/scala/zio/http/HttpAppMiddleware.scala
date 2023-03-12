package zio.http

import zio.http.middleware.{HttpRoutesMiddlewares, RequestHandlerMiddlewares}
import zio.{Trace, ZIO}

import zio.stacktracer.TracingImplicits.disableAutoTrace // scalafix:ok;

object HttpAppMiddleware extends RequestHandlerMiddlewares with HttpRoutesMiddlewares {
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

  trait Simple[-UpperEnv, +LowerErr] extends Contextual[Nothing, UpperEnv, LowerErr, Any] {
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
  def identity: HttpAppMiddleware[Nothing, Any, Nothing, Any] =
    new HttpAppMiddleware.Simple[Any, Nothing] {
      override def apply[Env, Err](
        http: Http[Env, Err, Request, Response],
      )(implicit trace: Trace): Http[Env, Err, Request, Response] =
        http
    }

  final class Allow(val unit: Unit) extends AnyVal {
    def apply(condition: Request => Boolean): HttpAppMiddleware[Nothing, Any, Nothing, Any] =
      new HttpAppMiddleware.Simple[Any, Nothing] {
        override def apply[Env, Err](
          http: Http[Env, Err, Request, Response],
        )(implicit trace: Trace): Http[Env, Err, Request, Response] =
          http.when(condition)
      }
  }

  final class AllowZIO(val unit: Unit) extends AnyVal {
    def apply[R, Err](
      condition: Request => ZIO[R, Err, Boolean],
    ): HttpAppMiddleware[Nothing, R, Err, Any] =
      new HttpAppMiddleware.Simple[R, Err] {
        override def apply[Env <: R, Err1 >: Err](
          http: Http[Env, Err1, Request, Response],
        )(implicit trace: Trace): Http[Env, Err1, Request, Response] =
          http.whenZIO(condition)
      }
  }

  implicit final class HttpAppMiddlewareSyntax[+LowerEnv, -UpperEnv, +LowerErr, -UpperErr](
    val self: HttpAppMiddleware[LowerEnv, UpperEnv, LowerErr, UpperErr],
  ) extends AnyVal {

    /**
     * Applies Middleware based only if the condition function evaluates to true
     */
    def when(
      condition: Request => Boolean,
    )(implicit trace: Trace): HttpAppMiddleware[LowerEnv, UpperEnv, LowerErr, UpperErr] =
      new HttpAppMiddleware.Contextual[LowerEnv, UpperEnv, LowerErr, UpperErr] {
        override type OutEnv[Env] = Env
        override type OutErr[Err] = Err

        override def apply[Env >: LowerEnv <: UpperEnv, Err >: LowerErr <: UpperErr](
          http: Http[Env, Err, Request, Response],
        )(implicit trace: Trace): Http[Env, Err, Request, Response] =
          Http.fromHttp { request =>
            val transformed = if (condition(request)) self(http) else http
            transformed
          }
      }

    /**
     * Applies Middleware based only if the condition effectful function
     * evaluates to true
     */
    def whenZIO[UpperEnv1 <: UpperEnv, LowerErr1 >: LowerErr](
      condition: Request => ZIO[UpperEnv1, LowerErr1, Boolean],
    )(implicit
      trace: Trace,
    ): HttpAppMiddleware[LowerEnv, UpperEnv1, LowerErr1, UpperErr] =
      new HttpAppMiddleware.Contextual[LowerEnv, UpperEnv1, LowerErr1, UpperErr] {
        override type OutEnv[Env] = Env
        override type OutErr[Err] = Err

        override def apply[Env >: LowerEnv <: UpperEnv1, Err >: LowerErr1 <: UpperErr](
          http: Http[Env, Err, Request, Response],
        )(implicit trace: Trace): Http[Env, Err, Request, Response] =
          Http.fromHttpZIO { request =>
            condition(request).map { condition =>
              val transformed = if (condition) self(http) else http
              transformed
            }
          }
      }
  }
}
