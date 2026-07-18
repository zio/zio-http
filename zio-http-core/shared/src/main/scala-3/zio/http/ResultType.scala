package zio.http

object ResultType {
  inline def responseAsResult(response: Response): Response | Halt = response
  inline def haltAsResult(halt: Halt): Response | Halt             = halt

  inline def foldResult[A](result: Response | Halt)(onResponse: Response => A, onHalt: Halt => A): A =
    result match {
      case r: Response => onResponse(r)
      case h: Halt     => onHalt(h)
    }
}
