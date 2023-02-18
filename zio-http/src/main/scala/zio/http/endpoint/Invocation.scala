package zio.http.endpoint

import zio.stacktracer.TracingImplicits.disableAutoTrace // scalafix:ok;

/**
 * An invocation represents a single invocation of an endpoint through provision
 * of the input that the endpoint requires.
 *
 * Invocations are pure data. In order to be useful, you must execute an
 * invocation with an [[EndpointExecutor]].
 */
final case class Invocation[I, E, O, M <: EndpointMiddleware](endpoint: Endpoint[I, E, O, M], input: I) {
  val middleware: endpoint.middleware.type = endpoint.middleware
}
