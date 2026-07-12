package zio.http

import scala.language.experimental

object HandlerMacros {
  def handler[H](h: H)(implicit th: ToHandler[H]): Handler[th.Ctx, th.Vars] =
    th.toHandler(h)
}
