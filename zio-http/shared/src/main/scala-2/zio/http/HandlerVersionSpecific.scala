package zio.http

import zio._

trait HandlerVersionSpecific {
  private[http] class ApplyContextAspect[-Env, +Err, -In, +Out, Env0](
    private val self: Handler[Env, Err, In, Out],
  ) {
    def apply[Env1, Ctx: Tag, In1 <: In](aspect: HandlerAspect[Env1, Ctx])(implicit
      in: Handler.IsRequest[In1],
      ev: Env0 with Ctx <:< Env,
      out: Out <:< Response,
      err: Err <:< Response,
      trace: Trace,
    ): Handler[Env0 with Env1, Response, In1, Response] =
      Handler.scoped[Env0 with Env1] {
        Handler.fromFunctionZIO[In1] { input =>
          aspect.protocol.incoming(in.request(input)).flatMap { case (state, (request, ctx)) =>
            self
              .asInstanceOf[Handler[Env, Response, In1, Response]](in.update(input, request))
              .provideSomeEnvironment[Scope & Env0 with Env1](
                _.add[Ctx](ctx).asInstanceOf[ZEnvironment[Scope & Env]],
              )
              .either
              .flatMap { either =>
                aspect.protocol
                  .outgoing(state, either.merge)
                  .flatMap(response => if (either.isLeft) ZIO.fail(response) else ZIO.succeed(response))
              }
          }
        }
      }
  }
}
