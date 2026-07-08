package zio.http

object ResultType {
  inline def responseAsResult(response: Response): Response | Halt = response
  inline def haltAsResult(halt: Halt): Response | Halt             = halt
}
