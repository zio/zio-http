package zio.http

import zio.blocks.context.Context
import zio.blocks.context.IsNominalType

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
   * The context must contain both a Server implementation and all
   * dependencies required by the routes.
   */
  def serve[Ctx](
    routes: Routes[Ctx],
    context: Context[Server with Ctx],
  )(implicit ev: IsNominalType[Server]): ServerHandle =
    context.get[Server].serve(routes, context)
}
