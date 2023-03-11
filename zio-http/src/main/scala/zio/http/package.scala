package zio

import zio.stacktracer.TracingImplicits.disableAutoTrace // scalafix:ok;

package object http extends PathSyntax with RequestSyntax with RouteDecoderModule {

  type RequestHandler[-R, +Err] = Handler[R, Err, Request, Response]

  type Middleware[+LowerEnv, -UpperEnv, +LowerErr, -UpperErr] =
    Middleware.Contextual[LowerEnv, UpperEnv, LowerErr, UpperErr] {
      type OutEnv[Env] = Env
      type OutErr[Err] = Err
    }

  type HandlerAspect[+LowerEnv, -UpperEnv, +LowerErr, -UpperErr] =
    HandlerAspect.Contextual[LowerEnv, UpperEnv, LowerErr, UpperErr] {
      type OutEnv[Env] = Env
      type OutErr[Err] = Err
    }

  type HandlerMiddleware[+LowerEnv, -UpperEnv, +LowerErr, -UpperErr] =
    HandlerMiddleware.Contextual[LowerEnv, UpperEnv, LowerErr, UpperErr] {
      type OutEnv[Env] = Env
      type OutErr[Err] = Err
    }

  type HttpAppMiddleware[-R, +Err] = Middleware[Nothing, R, Err, Any]
  object HttpAppMiddleware {
    type WithOut[-R, +Err, OutEnv0[_], OutErr0[_]] = Contextual[R, Err] {
      type OutEnv[Env]  = OutEnv0[Env]
      type OutErr[Err1] = OutErr0[Err1]
    }

    trait Contextual[-R, +Err] extends Middleware.Contextual[Nothing, R, Err, Any]

    trait Mono[-R, +Err] extends Middleware.Mono[Nothing, R, Err, Any]
  }

  type RequestHandlerMiddleware[-R, +Err] = HandlerMiddleware[Nothing, R, Err, Any]
  object RequestHandlerMiddleware {
    type WithOut[-R, +Err, OutEnv0[_], OutErr0[_]] =
      HandlerMiddleware.Contextual[Nothing, R, Err, Any] {
        type OutEnv[Env0] = OutEnv0[Env0]
        type OutErr[Err0] = OutErr0[Err0]
      }

    trait Contextual[-R, +Err] extends HandlerMiddleware.Contextual[Nothing, R, Err, Any]

    trait Mono[-R, +Err] extends HandlerMiddleware.Mono[Nothing, R, Err, Any]
  }

  type UMiddleware = Middleware[Nothing, Any, Nothing, Any]

  type HttpApp[-R, +Err] = Http[R, Err, Request, Response]
  type UHttpApp          = HttpApp[Any, Nothing]
  type RHttpApp[-R]      = HttpApp[R, Throwable]
  type EHttpApp          = HttpApp[Any, Throwable]
  type UHttp[-A, +B]     = Http[Any, Nothing, A, B]
  type App[-R]           = HttpApp[R, Response]

  type Client = ZClient[Any, Body, Throwable, Response]
  def Client: ZClient.type = ZClient
}
