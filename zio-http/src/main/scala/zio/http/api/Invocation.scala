package zio.http.api

import zio.stacktracer.TracingImplicits.disableAutoTrace // scalafix:ok;

final case class Invocation[A, E, B, M <: EndpointMiddleware](api: Endpoint[A, E, B, M], input: A)
