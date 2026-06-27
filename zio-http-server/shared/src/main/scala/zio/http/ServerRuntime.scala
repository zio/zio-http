package zio.http

import zio.blocks.context.Context

private[http] trait ServerRuntimePlatform {
  def serve[Ctx](server: Server[Ctx], context: Context[Ctx]): ServerHandle
}
