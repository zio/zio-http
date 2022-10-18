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
      // Type safety issues
      // If both in and out exists, handler should only be applied to `in`
      // If only either of them exist, handler shouldn't be applied to the one that is none
      case Middleware.HandlerZIO(middlewareSpec, handler) =>
        def loop[I1](
          in: HttpCodec[CodecType.Header with CodecType.Query, I1],
        ): Request => ZIO[R, Option[E], I1] = {

          in match {
            case atom: HttpCodec.Atom[CodecType.Header with CodecType.Query, _] =>
              atom match {
                case HttpCodec.Header(name, codec) =>
                  (request: Request) => ZIO.fromOption(request.headers.get(name).flatMap(codec.decode))

                case HttpCodec.Query(key, codec) =>
                  (request: Request) =>
                    ZIO.fromOption(
                      request.url.queryParams.get(key).flatMap(_.headOption).flatMap(codec.decode),
                    )

                case _ => _ => ZIO.fail(None)
              }

            case HttpCodec.WithDoc(in, _) =>
              loop(in)

            case HttpCodec.TransformOrFail(api, f, g) =>
              request => loop(api)(request).map(o => f(o).getOrElse(throw new Exception("uh oh!"))) // FIXME

            case HttpCodec.Combine(left, right, inputCombiner) =>
              request =>
                loop(left)(request).zip(loop(right)(request)).map { a =>
                  inputCombiner.combine(a._1, a._2)
                }
          }
        }

        val interceptFn = loop(middlewareSpec.middlewareIn) // FIXME handle middlewareOut

        zio.http.Middleware.interceptZIO[Request, Response](request =>
          interceptFn(request).flatMap(handler(_).mapError(Some(_))),
        )((a, _) => ZIO.succeed(a))

      case concat: Middleware.Concat[R, E, _, _, _, _, _, _] =>
        toHttpMiddleware(concat.left) ++ toHttpMiddleware(concat.right)
      case Middleware.Handler(_, _)                          => http.Middleware.empty // TODO
      case peek: Middleware.PeekRequest[_, _, _, _]          => toHttpMiddleware(peek.middleware)
    }
  }

}
