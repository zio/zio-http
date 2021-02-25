package zio.web

sealed trait Endpoints[-M, Identities] { self =>
  type Id = Identities

  final def +[M2](endpoint: Endpoint[M2, _, _]): Endpoints[M with M2, Identities with endpoint.Identity] =
    Endpoints.Cons[M2, M, endpoint.Identity, Identities, endpoint.type, self.type](endpoint, self)
}

object Endpoints {
  final case class Cons[M1, M2, Identity, Identities, E <: Endpoint.Aux[M1, _, _, Identity], T <: Endpoints[
    M2,
    Identities
  ]] private[web] (
    endpoint: E,
    tail: T
  ) extends Endpoints[M2 with M1, Identities with Identity]
  sealed trait Empty extends Endpoints[Any, Any]

  private[web] case object Empty extends Empty

  def apply[M1](e1: Endpoint[M1, _, _]): Endpoints[M1, e1.Identity] =
    empty + e1

  def apply[M1, M2](
    e1: Endpoint[M1, _, _],
    e2: Endpoint[M2, _, _]
  ): Endpoints[M1 with M2, e1.Identity with e2.Identity] =
    empty + e1 + e2

  val empty: Empty = Empty

  // trait ClientService[A <: Endpoints] {
  //   // def invoke[M, I, O](endpoint: Endpoint[M, I, O], request: I)(implicit get: Lens.Get[A, Endpoint[M, I, O]]): Task[O]
  // }
}
