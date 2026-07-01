package zio.http

object HandlerMacros {
  type ContextOf[H] = H match {
    case Handler[ctx, vars] => ctx
    case _                  => Any
  }

  type VarsOf[H] = H match {
    case Handler[ctx, vars] => vars
    case _                  => Any
  }

  inline def handler[H](inline h: H)(using th: ToHandler[H]): Handler[th.Ctx, th.Vars] =
    th.toHandler(h)
}
