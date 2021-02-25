package zio.web

sealed trait Handlers[-M[_], -R, Identities] { self =>
  final def +[M1[_] <: M[_], R1 <: R](
    that: Handler[M1, _, R1, _, _]
  ): Handlers[M1, R1, Identities with that.Identity] = {
    type Actual   = Handlers[M, R, Identities]
    type Expected = Handlers[M1, R1, Identities]

    def cast(actual: Actual): Expected = actual.asInstanceOf[Expected]

    Handlers.Cons[M1, R1, that.Identity, Identities, that.type, Handlers[M1, R1, Identities]](that, cast(self))
  }
}

object Handlers {
  type AnyF[+A] = Any

  final case class Cons[M1[_], R1, Identity, Identities, E <: Handler.Aux[M1, _, R1, _, _, Identity], T <: Handlers[
    M1,
    R1,
    Identities
  ]] private[web] (
    handler: E,
    tail: T
  ) extends Handlers[M1, R1, Identities with Identity]
  sealed trait Empty extends Handlers[AnyF, Any, Any]

  private[web] case object Empty extends Empty

  def apply[M1[_], R1](e1: Handler[M1, _, R1, _, _]): Handlers[M1, R1, e1.Identity] =
    empty + e1

  def apply[M1[_], R1](
    e1: Handler[M1, _, R1, _, _],
    e2: Handler[M1, _, R1, _, _]
  ): Handlers[M1, R1, e1.Identity with e2.Identity] =
    empty + e1 + e2

  val empty: Empty = Empty
}
