package zio.http

import zio._

trait HandlerVersionSpecific {
  private[http] class ApplyContextAspect[-Env, +Err, -In, +Out, Env0](private val self: Handler[Env, Err, In, Out]) {
    def apply[Env1, Ctx: Tag, In1 <: In](aspect: HandlerAspect[Env1, Ctx])(implicit
      in: Handler.IsRequest[In1],
      ev: Env0 with Ctx <:< Env,
      out: Out <:< Response,
      err: Err <:< Response,
      trace: Trace,
    ): Handler[Env0 with Env1, Response, In1, Response] =
      Handler.scoped[Env0 with Env1] {
        Handler.fromFunctionZIO[In1] { in0 =>
          val req0 = Handler.extractRequest(in0)
          aspect.applyHandlerContext {
            Handler.scoped[Env0] {
              handler { (ctx: Ctx, req: Request) =>
                val updatedIn = Handler.replaceRequest(in0, req)
                val h: ZIO[Scope & Env, Response, Response] =
                  self.asInstanceOf[Handler[Env, Response, In1, Response]](updatedIn)
                h.provideSomeEnvironment[Scope & Env0](_.add[Ctx](ctx).asInstanceOf[ZEnvironment[Scope & Env]])
              }
            }
          }(req0)
        }
      }
  }

}
