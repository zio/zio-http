package zio.http.api

import zio.http.{Http, HttpApp, Request, Response}
import zio.{Chunk, ZIO, http}

final case class ServiceSpec[-Ids, MIn, MErr, MOut](
  endpointSpecs: EndpointSpecs[Ids],
  middlewareSpec: MiddlewareSpec[MIn, MErr, MOut]
) { self =>

  final def apis: Chunk[EndpointSpec[_, _, _]] = endpointSpecs.specs
  
  final def toHttpApp[Ids1 <: Ids, R, E](
    service: Endpoints[R, E, Ids1]
  )(implicit alt: Alternator[E, MErr]): HttpApp[R, E] =
    service.toHttpApp
}