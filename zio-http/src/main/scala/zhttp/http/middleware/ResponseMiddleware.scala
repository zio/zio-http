package zhttp.http.middleware

import zhttp.http.{Header, Status}
import zio.ZIO

sealed trait ResponseMiddleware[-R, +E, -S]

object ResponseMiddleware {
  case class ConsM[R, E, S](f: (Status, List[Header], S) => ZIO[R, E, ResponsePatch])
      extends ResponseMiddleware[R, E, S]
  case class Cons[S](f: (Status, List[Header], S) => ResponsePatch) extends ResponseMiddleware[Any, Nothing, S]
  case object Identity                                              extends ResponseMiddleware[Any, Nothing, Unit]

  def identity: ResponseMiddleware[Any, Nothing, Unit] = Identity

  def makeM[S]: PartiallyAppliedMakeM[S] = PartiallyAppliedMakeM(())
  def make[S]: PartiallyAppliedMake[S]   = PartiallyAppliedMake(())

  final case class PartiallyAppliedMakeM[S](unit: Unit) extends AnyVal {
    def apply[R, E](f: (Status, List[Header], S) => ZIO[R, E, ResponsePatch]): ResponseMiddleware[R, E, S] = ConsM(f)
  }

  final case class PartiallyAppliedMake[S](unit: Unit) extends AnyVal {
    def apply(f: (Status, List[Header], S) => ResponsePatch): ResponseMiddleware[Any, Nothing, S] = Cons(f)
  }
}
