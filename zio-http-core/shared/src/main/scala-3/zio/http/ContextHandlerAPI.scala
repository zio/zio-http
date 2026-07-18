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
 * The macro generates a handler whose inferred type is structurally equivalent
 * to `Handler.extracted[C & D, Any]` where `C` and `D` are the context
 * parameter types. The public signature returns `Handler[?, ?]`; the precise
 * context type is preserved for the compiler through the `transparent inline`
 * modifier.
 */
transparent inline def contextHandler[H](inline h: H): Handler[?, ?] =
  ${ ContextHandlerMacro.impl[H]('h) }
