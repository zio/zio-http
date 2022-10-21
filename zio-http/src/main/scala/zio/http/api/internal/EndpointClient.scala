package zio.http.api.internal

import zio._
import zio.http._
import zio.http.api._
import zio.http.model.Headers
import zio.schema._
import zio.schema.codec._
import zio.stacktracer.TracingImplicits.disableAutoTrace // scalafix:ok;

private[api] final case class EndpointClient[I, O](apiRoot: URL, api: EndpointSpec[I, O]) {
  private val inputCodecs        = BodyCodec.findAll(api.input)
  private val outputCodecs       = BodyCodec.findAll(api.output)
  private val inputJsonEncoders  = inputCodecs.map(bodyCodec => bodyCodec.erase.encodeToBody(_, JsonCodec))
  private val outputJsonDecoders = outputCodecs.map(bodyCodec => bodyCodec.decodeFromBody(_, JsonCodec))

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
      val query = flattened.queries(i).asInstanceOf[HttpCodec.Query[Any]]
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
      val header = flattened.headers(i).asInstanceOf[HttpCodec.Header[Any]]
      val input  = inputs(i)

      val value = header.textCodec.encode(input)

      headers = headers ++ Headers(header.name, value)

      i = i + 1
    }

    headers
  }

  private def encodeMethod(inputs: Array[Any]): zio.http.model.Method = {
    if (flattened.methods.nonEmpty) {
      val method = flattened.methods.head.asInstanceOf[TextCodec[Any]]
      zio.http.model.Method.fromString(method.encode(inputs(0)))
    } else {
      zio.http.model.Method.GET
    }
  }

  private def encodeBody(inputs: Array[Any]): Body =
    if (inputJsonEncoders.length == 0) Body.empty
    else if (inputJsonEncoders.length == 1) {
      val encoder = inputJsonEncoders(0)

      encoder(inputs(0))
    } else throw new IllegalStateException("A request on a REST endpoint should have at most one body")

  def execute(client: Client, input: I)(implicit trace: Trace): ZIO[Any, Throwable, O] = {
    val inputs = deconstructor(input)

    val route   = encodeRoute(inputs.routes)
    val query   = encodeQuery(inputs.queries)
    val headers = encodeHeaders(inputs.headers)
    val body    = encodeBody(inputs.bodies)
    val method  = encodeMethod(inputs.methods)

    val request = Request
      .default(
        method,
        apiRoot ++ URL(route, URL.Location.Relative, query),
        body,
      )
      .copy(
        headers = headers,
      )

    client.request(request).flatMap { response =>
      if (outputJsonDecoders.length == 0) ZIO.succeed(().asInstanceOf[O])
      else {
        val decoder = outputJsonDecoders(0)

        decoder(response.body)
          .catchAll(error => ZIO.die(EndpointError.MalformedResponseBody(s"Could not decode response: $error", api)))
          .map(_.asInstanceOf[O])
      }
    }
  }
}
