package zio.http

import zio.blocks.context.{Context, ContextHas, IsNominalType}

/**
 * Server runtime interface. Implementations live in backend modules.
 *
 * Usage:
 * {{{
 * val context = Context.empty
 *   .add(LoomServer(Connector(port = 8080)))
 *   .add(myDatabase)
 *
 * val handle = Server.serve(routes, context)
 * }}}
 */
trait Server {
  def serve[Ctx](routes: Routes[Ctx], context: Context[Ctx]): ServerHandle
}

object Server {

  /**
   * Serve routes using the Server implementation from context.
   *
   * The context must contain both a [[Server]] implementation and all
   * dependencies required by the routes. If any component is missing,
   * [[zio.blocks.context.ContextHas]] emits a clear compile-time error listing
   * the missing types.
   *
   * @tparam ReqCtx
   *   the context type required by the routes
   * @tparam Ctx
   *   the actual context type provided — must be a supertype of
   *   `ReqCtx with Server`
   */
  def serve[ReqCtx, Ctx](
    routes: Routes[ReqCtx],
    context: Context[Ctx],
  )(implicit
    ev: ContextHas[Ctx, ReqCtx with Server],
    nt: IsNominalType[Server],
  ): ServerHandle = {
    // SAFETY: ContextHas[Ctx, ReqCtx with Server] proves Ctx <: ReqCtx with Server
    // at compile time. The cast is needed because Context.get[A >: R] requires a
    // subtype bound that Scala can't derive from implicit evidence alone.
    val ctx = context.asInstanceOf[Context[Server with ReqCtx]]
    ctx.get[Server].serve(routes, ctx.asInstanceOf[Context[ReqCtx]])
  }
}
