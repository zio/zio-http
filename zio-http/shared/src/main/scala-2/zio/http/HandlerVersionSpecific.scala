package zio.http

import zio._

trait HandlerVersionSpecific {
  private[http] class ApplyContextAspect[-Env, +Err, -In, +Out, Env0](private val self: Handler[Env, Err, In, Out]) {
    def apply[Env1, Ctx: Tag](aspect: HandlerAspect[Env1, Ctx])(implicit
      ev: Env0 with Ctx <:< Env,
      out: Out <:< Response,
      err: Err <:< Response,
      trace: Trace,
    ): Handler[Env0 with Env1, Response, In, Response] =
      Handler.scoped[Env0 with Env1] {
        Handler.fromFunctionZIO[In] { input =>
          val requestHandler = Handler.scoped[Env0 with Env1] {
            Handler.fromFunctionZIO[(Ctx, Request)] { tuple =>
              val (ctx, req)                                       = tuple
              val handler: ZIO[Scope with Env, Response, Response] =
                self
                  .asErrorType[Response]
                  .asOutType[Response]
                  .apply(Handler.updateInputRequest(input, req).asInstanceOf[In])
              handler.provideSomeEnvironment[Scope with Env0 with Env1](
                _.add[Ctx](ctx).asInstanceOf[ZEnvironment[Scope with Env]],
              )
            }
          }

          aspect.applyHandlerContext(requestHandler)(Handler.requestFromInput(input))
        }
      }
  }

}
