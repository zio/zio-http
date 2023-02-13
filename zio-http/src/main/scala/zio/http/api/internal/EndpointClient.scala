package zio.http.api.internal

import zio._
import zio.http._
import zio.http.api._
import zio.http.model.Headers
import zio.schema._
import zio.schema.codec._

private[api] final case class EndpointClient[I, O](apiRoot: URL, api: EndpointSpec[I, O]) {
  def execute(client: Client, input: I): ZIO[Any, Throwable, O] = {
    val request0 = api.input.encodeRequest(input)
    val request  = request0.copy(url = apiRoot ++ request0.url)

    client.request(request).flatMap(api.output.decodeResponse(_))
  }
}
