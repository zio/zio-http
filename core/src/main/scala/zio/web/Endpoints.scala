package zio.web

sealed trait Endpoints[-M, Identities] { self =>
  type Id = Identities

  final def ::[M1 <: M](endpoint: Endpoint[M1, _, _]): 
    Endpoints[M1, Identities with endpoint.Identity] =
      ???///Endpoints.Cons[endpoint.Metadata, endpoint.Identity, Identities, endpoint.type, self.type](endpoint, self)
}

object Endpoints {
  // final case class Cons[Min, Max, Identity, Identities, E <: Endpoint.Aux[M, _, _, Identity], T <: Endpoints[M, Identities]] private[web] 
  //   (endpoint: E, tail: T) extends Endpoints[M, Identities with Identity]
  sealed trait Empty extends Endpoints[Any, Any]

  private[web] case object Empty extends Empty

  val empty: Empty = Empty

  // trait ClientService[A <: Endpoints] {
  //   // def invoke[M, I, O](endpoint: Endpoint[M, I, O], request: I)(implicit get: Lens.Get[A, Endpoint[M, I, O]]): Task[O]
  // }
}