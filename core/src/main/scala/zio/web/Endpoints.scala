package zio.web

import zio.{ Has, Tag, ZIO }

sealed trait Endpoints[-M[+_], Identities] { self =>
  type Id = Identities

  final def +[M1[+_] <: M[_]](endpoint: Endpoint[M1, _, _, _]): Endpoints[M1, Identities with endpoint.Identity] =
    Endpoints.Cons[M1, endpoint.Identity, Identities, endpoint.type, Endpoints[M1, Identities]](
      endpoint,
      self.asInstanceOf[Endpoints[M1, Identities]]
    )

  def invoke[P, I, O](endpoint: Endpoint[M, P, I, O])(input: I, params: P)(
    implicit ev: Identities <:< endpoint.Identity,
    tt: Tag[ClientService[Identities]]
  ): ZIO[Has[ClientService[Identities]], Throwable, O] =
    ZIO.accessM[Has[ClientService[Identities]]](_.get.invoke(endpoint)(input, params))

  def invoke[I, O](endpoint: Endpoint[M, Unit, I, O])(input: I)(
    implicit ev: Identities <:< endpoint.Identity,
    tt: Tag[ClientService[Identities]]
  ): ZIO[Has[ClientService[Identities]], Throwable, O] =
    ZIO.accessM[Has[ClientService[Identities]]](_.get.invoke(endpoint)(input, ()))
}

object Endpoints {
  final case class Cons[M1[+_], Identity, Identities, E <: Endpoint.Aux[M1, _, _, _, Identity], T <: Endpoints[
    M1,
    Identities
  ]] private[web] (
    endpoint: E,
    tail: T
  ) extends Endpoints[M1, Identities with Identity]
  sealed trait Empty extends Endpoints[Any, Any]

  private[web] case object Empty extends Empty

  def apply[M1[+_]](e1: Endpoint[M1, _, _, _]): Endpoints[M1, e1.Identity] =
    empty + e1

  def apply[M1[+_]](
    e1: Endpoint[M1, _, _, _],
    e2: Endpoint[M1, _, _, _]
  ): Endpoints[M1, e1.Identity with e2.Identity] =
    empty + e1 + e2

  val empty: Empty = Empty
}
