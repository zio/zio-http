package zio.http

import scala.annotation.unchecked.uncheckedVariance

import zio.*

trait HandlerVersionSpecific {
  private[http] class ApplyContextAspect[-Env, +Err, -In, +Out, Env0](self: Handler[Env, Err, In, Out]) {
    transparent inline def apply[Env1, Ctx, In1 <: In](aspect: HandlerAspect[Env1, Ctx])(implicit
      in: Handler.IsRequest[In @uncheckedVariance],
      ev: Env0 with Ctx <:< Env,
      out: Out <:< Response,
      err: Err <:< Response,
      trace: Trace,
    ): Handler[Env0 with Env1, Response, in.Input, Response] =
      Handler.scoped[Env0 with Env1] {
        Handler.fromFunctionZIO[in.Input] { input =>
          aspect.applyHandlerContext {
            Handler.scoped[Env0] {
              handler { (ctx: Ctx, req: Request) =>
                self(in.replace(input, req))
                  .map(out)
                  .mapError(err)
                  .provideSomeEnvironment[Scope & Env0](_.add(ctx).asInstanceOf[ZEnvironment[Scope & Env]])
              }
            }
          }(in.request(input))
        }
      }
  }

}
