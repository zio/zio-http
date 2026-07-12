package zio.http

import scala.quoted.*

/**
 * Entry point for context-aware handler construction.
 *
 * Usage:
 * {{{
 *   val h = contextHandler { (req: Request, auth: AuthContext, reqId: RequestId) =>
 *     Response.text(auth.userId + " " + reqId.value)
 *   }
 * }}}
 *
 * This generates a `Handler.extracted[AuthContext & RequestId, Any]` that
 * extracts context values via `ctx.get[T]` at runtime.
 */
transparent inline def contextHandler[H](inline h: H): Handler[?, ?] =
  ${ ContextHandlerMacro.impl[H]('h) }
