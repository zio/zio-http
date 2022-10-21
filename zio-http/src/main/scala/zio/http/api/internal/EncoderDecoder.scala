package zio.http.api.internal

import zio._
import zio.http._
import zio.http.api._
import zio.http.model._
import zio.schema.codec._

final case class EncoderDecoder[-AtomTypes, Value](httpCodec: HttpCodec[AtomTypes, Value]) {
  private val bodyCodecs    = BodyCodec.findAll(httpCodec)
  private val constructor   = Mechanic.makeConstructor(httpCodec)
  private val deconstructor = Mechanic.makeDeconstructor(httpCodec)

  private val jsonEncoders = bodyCodecs.map(bodyCodec => bodyCodec.erase.encodeToBody(_, JsonCodec))
  private val jsonDecoders = bodyCodecs.map(bodyCodec => bodyCodec.decodeFromBody(_, JsonCodec))

  private val flattened: Mechanic.FlattenedAtoms = Mechanic.flatten(httpCodec)

  def decode(codec: Codec)(url: URL, status: Status, method: Method, headers: Headers, body: Body): Task[Value] = {
    val inputsBuilder = flattened.makeInputsBuilder()

    decodeRoutes(url.path, inputsBuilder.routes)
    decodeQuery(url.queryParams, inputsBuilder.queries)
    decodeStatus(inputsBuilder.statuses)
    decodeMethod(method, inputsBuilder.methods)
    decodeHeaders(headers, inputsBuilder.headers)

    decodeBody(body, inputsBuilder.bodies).as(constructor(inputsBuilder))
  }

  def encode(codec: Codec)(value: Value): (URL, Status, Method, Headers, Body) = {
    val inputs = deconstructor(value)

    val route   = encodeRoute(inputs.routes)
    val query   = encodeQuery(inputs.queries)
    val status  = encodeStatus(inputs.statuses)
    val method  = encodeMethod(inputs.methods)
    val headers = encodeHeaders(inputs.headers)
    val body    = encodeBody(inputs.bodies)

    (URL(route, queryParams = query), status, method, headers, body)
  }

  private def decodeRoutes(path: Path, inputs: Array[Any]): Unit = {
    var i = 0
    while (i < inputs.length) {
      val segment   = path.segments(i) // TODO: Validation
      val textCodec = flattened.routes(i).asInstanceOf[TextCodec[Any]]

      inputs(i) = textCodec.decode(path.toString).getOrElse(EndpointError.MalformedRoute(path, segment, textCodec))

      i = i + 1
    }
  }

  private def decodeQuery(queryParams: QueryParams, inputs: Array[Any]): Unit = {
    var i = 0
    while (i < flattened.queries.length) {
      val query = flattened.queries(i).asInstanceOf[HttpCodec.Query[Any]]

      val value = queryParams
        .getOrElse(query.name, Nil)
        .headOption
        .getOrElse(throw EndpointError.MissingQueryParam(query.name))

      inputs(i) =
        query.textCodec.decode(value).getOrElse(throw EndpointError.MalformedQueryParam(query.name, query.textCodec))

      i = i + 1
    }
  }

  private def decodeStatus(inputs: Array[Any]): Unit = {
    var i = 0
    while (i < inputs.length) {
      val textCodec = flattened.statuses(i).asInstanceOf[TextCodec[Any]]

      inputs(i) = textCodec.decode("200").getOrElse(EndpointError.MalformedStatus("200", textCodec))

      i = i + 1
    }
  }

  private def decodeMethod(method: Method, inputs: Array[Any]): Unit = {
    var i = 0
    while (i < inputs.length) {
      val textCodec = flattened.methods(i)

      inputs(i) =
        textCodec.decode(method.toString()).getOrElse(EndpointError.MalformedMethod(method.toString(), textCodec))

      i = i + 1
    }
  }

  private def decodeHeaders(headers: Headers, inputs: Array[Any]): Unit = {
    var i = 0
    while (i < flattened.headers.length) {
      val header = flattened.headers(i).asInstanceOf[HttpCodec.Header[Any]]

      val value = headers.get(header.name).getOrElse(throw EndpointError.MissingHeader(header.name))

      inputs(i) =
        header.textCodec.decode(value).getOrElse(throw EndpointError.MalformedHeader(header.name, header.textCodec))

      i = i + 1
    }
  }

  private def decodeBody(body: Body, inputs: Array[Any])(implicit trace: Trace): Task[Unit] =
    if (jsonDecoders.length == 0) ZIO.unit
    else if (jsonDecoders.length == 1) {
      val decoder = jsonDecoders(0)

      decoder(body).map { result => inputs(0) = result }
    } else {
      ZIO.foreachDiscard(jsonDecoders.zipWithIndex) { case (decoder, index) =>
        decoder(body).map { result => inputs(index) = result }
      }
    }

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

  private def encodeStatus(inputs: Array[Any]): Status = {
    if (flattened.statuses.length == 0) {
      Status.Ok
    } else {
      val statusString = flattened.statuses(0).asInstanceOf[TextCodec[Any]].encode(inputs(0))

      Status.fromInt(statusString.toInt).getOrElse(Status.Ok)
    }
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
    if (jsonEncoders.length == 0) Body.empty
    else if (jsonEncoders.length == 1) {
      val encoder = jsonEncoders(0)

      encoder(inputs(0))
    } else throw new IllegalStateException("A request on a REST endpoint should have at most one body")
}
