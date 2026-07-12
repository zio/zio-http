package zio.http

import scala.language.implicitConversions

object ResultType {
  type |[+A, +B] = Either[A, B]
  implicit def responseAsResult(response: Response): Response | Halt = Left(response)
  implicit def haltAsResult(halt: Halt): Response | Halt             = Right(halt)

  def foldResult[A](result: Response | Halt)(onResponse: Response => A, onHalt: Halt => A): A =
    result match {
      case Left(r) => onResponse(r)
      case Right(h) => onHalt(h)
    }
}
