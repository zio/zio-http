package zio.http

import zio.*

trait HandlerVersionSpecific {
  private[http] class ApplyContextAspect[-Env, +Err, -In, +Out, Env0](self: Handler[Env, Err, In, Out]) {
    transparent inline def apply[Env1, Ctx, In1 <: In](aspect: HandlerAspect[Env1, Ctx])(implicit
      in: Handler.IsRequest[In1],
      ev: Env0 with Ctx <:< Env,
      out: Out <:< Response,
      err: Err <:< Response,
      trace: Trace,
    ): Handler[Env0 with Env1, Response, In1, Response] =
      Handler.fromFunctionHandler[In1] { in1 =>
        val req = in.extractRequest(in1)
        aspect
          .applyHandlerContext {
            Handler.scoped[Env0] {
              handler { (ctx: Ctx, modifiedReq: Request) =>
                val handler: ZIO[Scope & Env, Response, Response] =
                  self.asInstanceOf[Handler[Env, Response, In1, Response]](in.updateRequest(in1, modifiedReq))
                handler.provideSomeEnvironment[Scope & Env0](_.add(ctx).asInstanceOf[ZEnvironment[Scope & Env]])
              }
            }
          }
          .contramap[In1](_ => req)
      }
  }

}
