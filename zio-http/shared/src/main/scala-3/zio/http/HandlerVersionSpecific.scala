package zio.http

import zio.*

trait HandlerVersionSpecific {
  private[http] class ApplyContextAspect[-Env, +Err, -In, +Out, Env0](self: Handler[Env, Err, In, Out]) {
    transparent inline def apply[Env1, Ctx](aspect: HandlerAspect[Env1, Ctx])(implicit
      ev: Env0 with Ctx <:< Env,
      out: Out <:< Response,
      err: Err <:< Response,
      trace: Trace,
    ): Handler[Env0 with Env1, Response, In, Response] =
      Handler.scoped[Env0 with Env1] {
        Handler.fromFunctionZIO[In] { input =>
          val requestHandler = Handler.scoped[Env0 with Env1] {
            Handler.fromFunctionZIO[(Ctx, Request)] { tuple =>
              val (ctx, req) = tuple
              val handler: ZIO[Scope & Env, Response, Response] =
                self
                  .asErrorType[Response]
                  .asOutType[Response]
                  .apply(Handler.updateInputRequest(input, req).asInstanceOf[In])
              handler.provideSomeEnvironment[Scope & Env0 & Env1](_.add(ctx).asInstanceOf[ZEnvironment[Scope & Env]])
            }
          }

          aspect.applyHandlerContext(requestHandler)(Handler.requestFromInput(input))
        }
      }
  }

}
