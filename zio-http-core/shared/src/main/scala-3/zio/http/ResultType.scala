package zio.http

object ResultType {
  inline def responseAsResult(response: Response): Response | Halt = response
  inline def haltAsResult(halt: Halt): Response | Halt             = halt

  inline def foldResult[A](result: Response | Halt)(onResponse: Response => A, onHalt: Halt => A): A =
    result match {
      case r: Response => onResponse(r)
      case h: Halt     => onHalt(h)
    }

  inline def haltAsOutcome[S](halt: Halt): Halt | S = halt
  inline def valueAsOutcome[S](value: S): Halt | S  = value

  inline def foldOutcome[S, Z](outcome: Halt | S)(onHalt: Halt => Z, onValue: S => Z): Z =
    outcome match {
      case h: Halt @unchecked => onHalt(h)
      case s: S @unchecked    => onValue(s)
    }
}
