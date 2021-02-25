package zio.web

sealed trait Handlers[-M, -R, Identities] { self =>
  type Id = Identities

  final def +[M2, R2](
    Handler: Handler[M2, R2, _, _]
  ): Handlers[M with M2, R with R2, Identities with Handler.Identity] =
    Handlers.Cons[M2, M, R2, R, Handler.Identity, Identities, Handler.type, self.type](Handler, self)
}

object Handlers {
  final case class Cons[M1, M2, R1, R2, Identity, Identities, E <: Handler.Aux[M1, R1, _, _, Identity], T <: Handlers[
    M2,
    R2,
    Identities
  ]] private[web] (
    handler: E,
    tail: T
  ) extends Handlers[M2 with M1, R2 with R1, Identities with Identity]
  sealed trait Empty extends Handlers[Any, Any, Any]

  private[web] case object Empty extends Empty

  def apply[M1, R1](e1: Handler[M1, R1, _, _]): Handlers[M1, R1, e1.Identity] =
    empty + e1

  def apply[M1, R1, M2, R2](
    e1: Handler[M1, R1, _, _],
    e2: Handler[M2, R2, _, _]
  ): Handlers[M1 with M2, R1 with R2, e1.Identity with e2.Identity] =
    empty + e1 + e2

  val empty: Empty = Empty
}
