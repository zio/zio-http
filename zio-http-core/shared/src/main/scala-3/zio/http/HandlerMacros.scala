package zio.http

object HandlerMacros {
  inline def handler[H](inline h: H)(using th: ToHandler[H]): Handler[th.Ctx, th.Vars] =
    th.toHandler(h)
}
