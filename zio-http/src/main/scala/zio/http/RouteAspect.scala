package zio.http

import zio._

trait RouteAspect[+LowerEnv, -UpperEnv] { self =>
  def apply[Env1 >: LowerEnv <: UpperEnv](
    handler: Handler[Env1, Response, Request, Response],
  ): Handler[Env1, Response, Request, Response]

  def @@[LowerEnv1 >: LowerEnv, UpperEnv1 <: UpperEnv](
    that: RouteAspect[LowerEnv1, UpperEnv1],
  ): RouteAspect[LowerEnv1, UpperEnv1] =
    self ++ that

  def ++[LowerEnv1 >: LowerEnv, UpperEnv1 <: UpperEnv](
    that: RouteAspect[LowerEnv1, UpperEnv1],
  ): RouteAspect[LowerEnv1, UpperEnv1] =
    new RouteAspect[LowerEnv1, UpperEnv1] {
      def apply[Env1 >: LowerEnv1 <: UpperEnv1](
        handler: Handler[Env1, Response, Request, Response],
      ): Handler[Env1, Response, Request, Response] =
        self(that(handler))
    }
}
object RouteAspect                      {
  val identity: RouteAspect[Nothing, Any] =
    new RouteAspect[Nothing, Any] {
      def apply[Env1 >: Nothing <: Any](
        handler: Handler[Env1, Response, Request, Response],
      ): Handler[Env1, Response, Request, Response] =
        handler
    }

  def timeout(duration: Duration): RouteAspect[Nothing, Any] =
    new RouteAspect[Nothing, Any] {
      def apply[Env1 >: Nothing <: Any](
        handler: Handler[Env1, Response, Request, Response],
      ): Handler[Env1, Response, Request, Response] =
        handler.timeoutFail(Response(status = Status.RequestTimeout))(duration)
    }
}
