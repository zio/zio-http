package zio.http.api.internal

import zio._
import zio.http._
import zio.http.api._
import zio.http.api.internal.Mechanic.Constructor
import zio.http.model.{Headers}
import zio.schema._
import zio.schema.codec._
import zio.stacktracer.TracingImplicits.disableAutoTrace // scalafix:ok;

private[api] final case class EndpointServer[R, E, I, O, M <: EndpointMiddleware](
  single: Routes.Single[R, E, I, O, M],
) {
  private val api     = single.endpoint
  private val handler = single.handler

  def handle(request: Request)(implicit trace: Trace): ZIO[R, E, Response] =
    api.input.decodeRequest(request).orDie.flatMap { value =>
      handler(value).map(api.output.encodeResponse(_))
    }
}
