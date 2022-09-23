package zio.http.api.internal

import zio._
import zio.http._
import zio.http.api._
import zio.http.model.Headers
import zio.schema._
import zio.schema.codec._
import zio.stacktracer.TracingImplicits.disableAutoTrace // scalafix:ok;

private[api] final case class APIClient[I, O](apiRoot: URL, api: API[I, O]) {
  private val optionSchema: Option[Schema[Any]]    = api.input.bodySchema.map(_.asInstanceOf[Schema[Any]])
  private val inputJsonEncoder: Any => Chunk[Byte] =
    JsonCodec.encode(optionSchema.getOrElse(Schema[Unit].asInstanceOf[Schema[Any]]))
  private val outputJsonDecoder: Chunk[Byte] => Either[String, Any] =
    JsonCodec.decode(api.output.bodySchema.asInstanceOf[Schema[Any]])
  private val deconstructor = Mechanic.makeDeconstructor(api.input).asInstanceOf[Mechanic.Deconstructor[Any]]
  private val flattened     = Mechanic.flatten(api.input)

  private def encodeRoute(inputs: Array[Any]): Path = {
    var path = Path.empty

    var i = 0
    while (i < inputs.length) {
      val textCodec = flattened.routes(i).asInstanceOf[TextCodec[Any]]
      val input     = inputs(i)

      val segment = textCodec.encode(input)

      path = path / segment
      i = i + 1
    }

    path
  }

  private def encodeQuery(inputs: Array[Any]): QueryParams = {
    var queryParams = QueryParams.empty

    var i = 0
    while (i < inputs.length) {
      val query = flattened.queries(i).asInstanceOf[In.Query[Any]]
      val input = inputs(i)

      val value = query.textCodec.encode(input)

      queryParams = queryParams.add(query.name, value)

      i = i + 1
    }

    queryParams
  }

  private def encodeHeaders(inputs: Array[Any]): Headers = {
    var headers = Headers.contentType("application/json")

    var i = 0
    while (i < inputs.length) {
      val header = flattened.headers(i).asInstanceOf[In.Header[Any]]
      val input  = inputs(i)

      val value = header.textCodec.encode(input)

      headers = headers ++ Headers(header.name, value)

      i = i + 1
    }

    headers
  }

  private def encodeBody(inputs: Array[Any]): Body =
    if (inputs.length == 0) Body.empty
    else Body.fromChunk(inputJsonEncoder(inputs(0)))

  def execute(client: Client, input: I)(implicit trace: Trace): ZIO[Any, Throwable, O] = {
    val inputs = deconstructor(input)

    val route   = encodeRoute(inputs.routes)
    val query   = encodeQuery(inputs.queries)
    val headers = encodeHeaders(inputs.headers)
    val body    = encodeBody(inputs.inputBodies)

    val request = Request
      .default(
        api.method,
        apiRoot ++ URL(route, URL.Location.Relative, query),
        body,
      )
      .copy(
        headers = headers,
      )

    client.request(request).flatMap { response =>
      response.body.asChunk.flatMap { response =>
        outputJsonDecoder(response) match {
          case Left(error)  => ZIO.die(APIError.MalformedResponseBody(s"Could not decode response: $error", api))
          case Right(value) => ZIO.succeed(value.asInstanceOf[O])
        }
      }
    }
  }
}
