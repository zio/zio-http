package zio.http.api.internal

import zio._
import zio.http._
import zio.http.api._
import zio.http.model.Headers
import zio.schema._
import zio.schema.codec._
import zio.stacktracer.TracingImplicits.disableAutoTrace // scalafix:ok;

private[api] final case class EndpointClient[I, E, O](apiRoot: URL, api: EndpointSpec[I, E, O]) {
  def execute(client: Client, input: I)(implicit trace: Trace): ZIO[Any, E, O] = {
    val request0 = api.input.encodeRequest(input)
    val request  = request0.copy(url = apiRoot ++ request0.url)

    client.request(request).orDie.flatMap { response =>
      if (response.status.isSuccess) {
        api.output.decodeResponse(response).orDie
      } else {
        api.error.decodeResponse(response).orDie.flatMap(ZIO.fail(_))
      }
    }
  }
}
