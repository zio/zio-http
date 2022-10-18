package zio.http.api

import zio.http.{HttpApp, Request, Response}
import zio.{Chunk, ZIO, http}

sealed trait ServiceSpec[MI, MO, -AllIds] { self =>
  final def ++[AllIds2](that: ServiceSpec[MI, MO, AllIds2]): ServiceSpec[MI, MO, AllIds with AllIds2] =
    ServiceSpec.Concat[MI, MO, AllIds, AllIds2](self, that)

  final def apis: Chunk[API[_, _]] = ServiceSpec.apisOf(self)

  final def middleware[MI2, MO2](
    ms: MiddlewareSpec[MI2, MO2],
  )(implicit mi: Combiner[MI, MI2], mo: Combiner[MO, MO2]): ServiceSpec[mi.Out, mo.Out, AllIds] =
    ServiceSpec.AddMiddleware[MI, MI2, mi.Out, MO, MO2, mo.Out, AllIds](self, ms, mi, mo)

  final def middlewareSpec: MiddlewareSpec[_, _] =
    ServiceSpec.middlewareSpecOf(self)

  final def toHttpApp[AllIds1 <: AllIds, R, E](
    service: Service[R, E, AllIds1],
  )(implicit ev1: MI =:= Unit, ev2: MO =:= Unit): HttpApp[R, E] =
    self.withMI[Unit].withMO[Unit].toHttpApp(service, Middleware.none)

  final def toHttpApp[AllIds1 <: AllIds, R, E](
    service: Service[R, E, AllIds1],
    midddleware: Middleware[R, E, MI, MO],
  ): HttpApp[R, E] =
    service.toHttpApp

  final def withMI[MI2](implicit ev: MI =:= MI2): ServiceSpec[MI2, MO, AllIds] =
    self.asInstanceOf[ServiceSpec[MI2, MO, AllIds]]

  final def withMO[MO2](implicit ev: MO =:= MO2): ServiceSpec[MI, MO2, AllIds] =
    self.asInstanceOf[ServiceSpec[MI, MO2, AllIds]]
}
object ServiceSpec                        {
  private case object Empty                                      extends ServiceSpec[Unit, Unit, Any]
  private final case class Single[Id](api: API.WithId[Id, _, _]) extends ServiceSpec[Unit, Unit, Id]
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

  def apply[A <: API[_, _]](api: A): ServiceSpec[Unit, Unit, api.Id] =
    Single(api.asInstanceOf[API.WithId[api.Id, Any, Any]])

  def empty: ServiceSpec[Unit, Unit, Any] = Empty

  private def apisOf(self: ServiceSpec[_, _, _]): Chunk[API[_, _]] =
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

  def toHttpMiddleware[R, E, I, O](
    middleware: Middleware[R, E, I, O],
  ): http.Middleware[R, E, Request, Response, Request, Response] = {
    middleware match {
      case Middleware.HandlerZIO(middlewareSpec, handler) =>
        def loop[I1](
          in: HttpCodec[CodecType.Header with CodecType.Query, I1],
        ): Request => ZIO[R, Option[E], Any] =
          in match {
            case atom: HttpCodec.Atom[CodecType.Header with CodecType.Query, _] =>
              atom match {
                case HttpCodec.Header(name, codec) =>
                  (request: Request) =>
                    handler {
                      codec.decode(request.headers.get(name).get).get.asInstanceOf[I]
                    }.mapError(Some(_))

                case HttpCodec.Query(key, codec) =>
                  (request: Request) =>
                    handler(codec.decode(request.url.queryParams.get(key).head.head).get.asInstanceOf[I])
                      .mapError(Some(_))

                case _ => throw new Exception("cannot happen") // Can't we get a compile time safety here?
              }

            case HttpCodec.WithDoc(in, doc)           =>
              loop(in)
            case HttpCodec.TransformOrFail(api, f, g) =>
              request => loop(api)(request).map(o => f(o).getOrElse(throw new Exception("uh oh!"))) // FIXME

            case HttpCodec.Combine(left, right, inputCombiner) =>
              request =>
                loop(left)(request).zip(loop(right)(request)).map { a =>
                  inputCombiner.combine(a._1, a._2)
                }
          }

        val interceptFn = loop(middlewareSpec.middlewareIn) // FIXME handle middlewareOut
        zio.http.Middleware.interceptZIO[Request, Response](interceptFn)((a, r) => ZIO.succeed(a))

      case concat: Middleware.Concat[R, E, i1, o1, i2, o2, i3, o3] =>
        toHttpMiddleware(concat.left) ++ toHttpMiddleware(concat.right)
      case Middleware.Handler(middlewareSpec, handler)             => http.Middleware.empty // TODO
      case peek: Middleware.PeekRequest[R, E, i, o]                => toHttpMiddleware(middleware)
    }
  }

}
