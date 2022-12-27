package zio.http.api.internal

import zio._
import zio.http._
import zio.http.api._
import zio.http.api.internal.Mechanic.Constructor
import zio.http.model.{Headers}
import zio.schema._
import zio.schema.codec._
import zio.stacktracer.TracingImplicits.disableAutoTrace // scalafix:ok;

private[api] final case class EndpointServer[R, E, I, O](handledEndpoint: Endpoints.HandledEndpoint[R, E, I, O, _]) {
  private val api     = handledEndpoint.endpointSpec
  private val handler = handledEndpoint.handler

  def handle(routeInputs: Chunk[Any], request: Request)(implicit trace: Trace): ZIO[R, E, Response] = {
    api.input.decodeRequest(request).orDie.flatMap { value =>
      handler(value).map { output =>
        api.output.encodeResponse(output)
      }
    }
  }
}
