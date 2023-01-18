package zio.http.api

import zio.Chunk
import zio.http.HttpApp

sealed trait ServiceSpec[MI, MO, -AllIds] { self =>
  final def ++[AllIds2](that: ServiceSpec[MI, MO, AllIds2]): ServiceSpec[MI, MO, AllIds with AllIds2] =
    ServiceSpec.Concat[MI, MO, AllIds, AllIds2](self, that)

  final def apis: Chunk[EndpointSpec[_, _]] = ServiceSpec.apisOf(self)

  final def middleware[MI2, MO2](
    ms: MiddlewareSpec[MI2, MO2],
  )(implicit mi: Combiner[MI, MI2], mo: Combiner[MO, MO2]): ServiceSpec[mi.Out, mo.Out, AllIds] =
    ServiceSpec.AddMiddleware[MI, MI2, mi.Out, MO, MO2, mo.Out, AllIds](self, ms, mi, mo)

  final def middlewareSpec: MiddlewareSpec[_, _] =
    ServiceSpec.middlewareSpecOf(self)

  // TODO: rename these and create ToHttpApp that converts it to App[R]
  final def toHttpApp[AllIds1 <: AllIds, R, E](
    service: Endpoints[R, E, AllIds1],
  )(implicit ev1: MI =:= Unit, ev2: MO =:= Unit): HttpApp[R, E] =
    self.withMI[Unit].withMO[Unit].toHttpApp(service, Middleware.none)

  final def toHttpApp[AllIds1 <: AllIds, R, E](
    service: Endpoints[R, E, AllIds1],
    middleware: Middleware[R, MI, MO],
  ): HttpApp[R, E] =
    middleware(service.toHttpRoute)

  final def withMI[MI2](implicit ev: MI =:= MI2): ServiceSpec[MI2, MO, AllIds] =
    self.asInstanceOf[ServiceSpec[MI2, MO, AllIds]]

  final def withMO[MO2](implicit ev: MO =:= MO2): ServiceSpec[MI, MO2, AllIds] =
    self.asInstanceOf[ServiceSpec[MI, MO2, AllIds]]
}
object ServiceSpec                        {
  private case object Empty                                    extends ServiceSpec[Unit, Unit, Any]
  private final case class Single[Id](api: EndpointSpec[_, _]) extends ServiceSpec[Unit, Unit, Id]
  private final case class Concat[MI, MO, AllIds1, AllIds2](
    left: ServiceSpec[MI, MO, AllIds1],
    right: ServiceSpec[MI, MO, AllIds2],
  ) extends ServiceSpec[MI, MO, AllIds1 with AllIds2]
  private final case class AddMiddleware[MI1, MI2, MI3, MO1, MO2, MO3, AllIds](
    spec: ServiceSpec[MI1, MO1, AllIds],
    middlewareSpec0: MiddlewareSpec[MI2, MO2],
    mi: Combiner.WithOut[MI1, MI2, MI3],
    mo: Combiner.WithOut[MO1, MO2, MO3],
  ) extends ServiceSpec[MI3, MO3, AllIds]

  def apply(api: EndpointSpec[_, _]): ServiceSpec[Unit, Unit, api.type] =
    Single(api.asInstanceOf[EndpointSpec[Any, Any]])

  def empty: ServiceSpec[Unit, Unit, Any] = Empty

  private def apisOf(self: ServiceSpec[_, _, _]): Chunk[EndpointSpec[_, _]] =
    self match {
      case Empty                     => Chunk.empty
      case Concat(a, b)              => apisOf(a) ++ apisOf(b)
      case Single(a)                 => Chunk.single(a)
      case AddMiddleware(a, _, _, _) => apisOf(a)
    }

  private def middlewareSpecOf[MI2, MO2](self: ServiceSpec[MI2, MO2, _]): MiddlewareSpec[_, _] = {
    // FIXME: this is only WIP
    self match {
      case Empty                                   => MiddlewareSpec.none
      case Single(_)                               => MiddlewareSpec.none
      case Concat(left, right)                     => middlewareSpecOf(left).++(middlewareSpecOf(right))
      case AddMiddleware(_, middlewareSpec0, _, _) => middlewareSpec0
    }
  }
}
