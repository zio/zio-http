package zio.http

import scala.language.implicitConversions

object ResultType {
  type |[+A, +B] = Either[A, B]
  implicit def responseAsResult(response: Response): Response | Halt = Left(response)
  implicit def haltAsResult(halt: Halt): Response | Halt             = Right(halt)
}
