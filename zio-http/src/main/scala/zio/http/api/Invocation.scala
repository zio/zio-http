package zio.http.api

import zio.stacktracer.TracingImplicits.disableAutoTrace // scalafix:ok;

final case class Invocation[I, E, O, M <: EndpointMiddleware](endpoint: Endpoint[I, E, O, M], input: I) {
  val middleware: endpoint.middleware.type = endpoint.middleware
}
