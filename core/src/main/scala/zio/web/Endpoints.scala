package zio.web

import zio.{ Has, Tag, ZIO }

/**
 * An `Endpoints[M, Ids]` represents an ordered collection of endpoints with identifiers `Ids` and minimum metadata `M`.
 */
sealed trait Endpoints[-M[+_], Ids0] { self =>
  type Ids = Ids0

  final def +[M1[+_] <: M[_]](endpoint: Endpoint[M1, _, _, _]): Endpoints[M1, Ids with endpoint.Id] =
    Endpoints.Cons[M1, endpoint.Id, Ids, endpoint.type, Endpoints[M1, Ids]](
      endpoint,
      self.asInstanceOf[Endpoints[M1, Ids]]
    )

  def invoke[P, I, O](endpoint: Endpoint[M, P, I, O])(input: I, params: P)(
    implicit ev: Ids <:< endpoint.Id,
    tt: Tag[ClientService[Ids]]
  ): ZIO[Has[ClientService[Ids]], Throwable, O] =
    ZIO.accessM[Has[ClientService[Ids]]](_.get.invoke(endpoint)(input, params))

  def invoke[I, O](endpoint: Endpoint[M, Unit, I, O])(input: I)(
    implicit ev: Ids <:< endpoint.Id,
    tt: Tag[ClientService[Ids]]
  ): ZIO[Has[ClientService[Ids]], Throwable, O] =
    ZIO.accessM[Has[ClientService[Ids]]](_.get.invoke(endpoint)(input, ()))
}

object Endpoints {
  final case class Cons[M1[+_], Id, Ids, E <: Endpoint.Aux[M1, _, _, _, Id], T <: Endpoints[M1, Ids]] private[web] (
    endpoint: E,
    tail: T
  ) extends Endpoints[M1, Ids with Id]
  sealed trait Empty extends Endpoints[Any, Any]

  private[web] case object Empty extends Empty

  def apply[M1[+_]](e1: Endpoint[M1, _, _, _]): Endpoints[M1, e1.Id] =
    empty + e1

  def apply[M1[+_]](
    e1: Endpoint[M1, _, _, _],
    e2: Endpoint[M1, _, _, _]
  ): Endpoints[M1, e1.Id with e2.Id] =
    empty + e1 + e2

  val empty: Empty = Empty
}
