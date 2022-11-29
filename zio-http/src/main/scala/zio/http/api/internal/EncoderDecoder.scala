package zio.http.api.internal

import zio._
import zio.http._
import zio.http.api._
import zio.http.model._
import zio.schema.codec._

import zio.stacktracer.TracingImplicits.disableAutoTrace // scalafix:ok;

private[api] final case class EncoderDecoder[-AtomTypes, Value](httpCodec: HttpCodec[AtomTypes, Value]) {
  private val constructor   = Mechanic.makeConstructor(httpCodec)
  private val deconstructor = Mechanic.makeDeconstructor(httpCodec)

  private val flattened: Mechanic.FlattenedAtoms = Mechanic.flatten(httpCodec)

  private val jsonEncoders = flattened.bodies.map(bodyCodec => bodyCodec.erase.encodeToBody(_, JsonCodec))
  private val jsonDecoders = flattened.bodies.map(bodyCodec => bodyCodec.decodeFromBody(_, JsonCodec))

  def decode(url: URL, status: Status, method: Method, headers: Headers, body: Body)(implicit
    trace: Trace,
  ): Task[Value] = {
    val inputsBuilder = flattened.makeInputsBuilder()

    decodeRoutes(url.path, inputsBuilder.routes)
    decodeQuery(url.queryParams, inputsBuilder.queries)
    decodeStatus(status, inputsBuilder.statuses)
    decodeMethod(method, inputsBuilder.methods)
    decodeHeaders(headers, inputsBuilder.headers)
    decodeBody(body, inputsBuilder.bodies).as(constructor(inputsBuilder))
  }

  final def encodeWith[Z](value: Value)(f: (URL, Option[Status], Option[Method], Headers, Body) => Z): Z = {
    val inputs = deconstructor(value)

    val route   = encodeRoute(inputs.routes)
    val query   = encodeQuery(inputs.queries)
    val status  = encodeStatus(inputs.statuses)
    val method  = encodeMethod(inputs.methods)
    val headers = encodeHeaders(inputs.headers)
    val body    = encodeBody(inputs.bodies)

    f(URL(route, queryParams = query), status, method, headers, body)
  }

  private def decodeRoutes(path: Path, inputs: Array[Any]): Unit = {
    assert(flattened.routes.length == inputs.length)

    var i        = 0
    var j        = 0
    val segments = path.segments
    while (i < inputs.length) {
      val textCodec = flattened.routes(i).erase

      if (j >= segments.length) throw EndpointError.PathTooShort(path, textCodec)
      else {
        val segment = segments(j)

        if (segment.text.length != 0) {
          val textCodec = flattened.routes(i).erase

          inputs(i) =
            textCodec.decode(segment.text).getOrElse(throw EndpointError.MalformedRoute(path, segment, textCodec))

          i = i + 1
        }
        j = j + 1
      }
    }
  }

  private def decodeQuery(queryParams: QueryParams, inputs: Array[Any]): Unit = {
    var i       = 0
    val queries = flattened.queries
    while (i < queries.length) {
      val query = queries(i).erase

      val queryParamValue =
        queryParams
          .getOrElse(query.name, Nil)
          .collectFirst(query.textCodec)

      queryParamValue match {
        case Some(value) =>
          inputs(i) = value
        case None        =>
          if (query.optional) {
            inputs(i) = Undefined
          } else throw EndpointError.MissingQueryParam(query.name)
      }

      i = i + 1
    }
  }

  private def decodeStatus(status: Status, inputs: Array[Any]): Unit = {
    var i = 0
    while (i < inputs.length) {
      val textCodec = flattened.statuses(i).erase

      inputs(i) = textCodec.decode(status.text).getOrElse(throw EndpointError.MalformedStatus("200", textCodec))

      i = i + 1
    }
  }

  private def decodeMethod(method: Method, inputs: Array[Any]): Unit = {
    var i = 0
    while (i < inputs.length) {
      val textCodec = flattened.methods(i)

      inputs(i) =
        textCodec.decode(method.text).getOrElse(throw EndpointError.MalformedMethod(method.toString(), textCodec))

      i = i + 1
    }
  }

  private def decodeHeaders(headers: Headers, inputs: Array[Any]): Unit = {
    var i = 0
    while (i < flattened.headers.length) {
      val header = flattened.headers(i).erase

      headers.get(header.name) match {
        case Some(value) =>
          inputs(i) = header.textCodec.swap
            .map(_.decode(value))
            .getOrElse(throw EndpointError.MalformedHeader(header.name, header.textCodec))

        case None =>
          if (header.optional) {
            inputs(i) = Undefined
          } else throw EndpointError.MissingHeader(header.name)
      }

      i = i + 1
    }
  }

  private def decodeBody(body: Body, inputs: Array[Any])(implicit trace: Trace): Task[Unit] =
    if (jsonDecoders.length == 0) ZIO.unit
    else if (jsonDecoders.length == 1) {
      jsonDecoders(0)(body).map { result => inputs(0) = result }
    } else {
      ZIO.foreachDiscard(jsonDecoders.zipWithIndex) { case (decoder, index) =>
        decoder(body).map { result => inputs(index) = result }
      }
    }

  private def encodeRoute(inputs: Array[Any]): Path = {
    var path = Path.empty

    var i = 0
    while (i < inputs.length) {
      val textCodec = flattened.routes(i).erase
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
      val query = flattened.queries(i).erase
      val input = inputs(i)

      val value = query.textCodec.encode(input)

      queryParams = queryParams.add(query.name, value)

      i = i + 1
    }

    queryParams
  }

  private def encodeStatus(inputs: Array[Any]): Option[Status] = {
    if (flattened.statuses.length == 0) {
      None
    } else {
      val statusString = flattened.statuses(0).erase.encode(inputs(0))

      Some(Status.fromInt(statusString.toInt).getOrElse(Status.Ok))
    }
  }

  private def encodeHeaders(inputs: Array[Any]): Headers = {
    var headers = Headers.contentType("application/json")

    var i = 0
    while (i < inputs.length) {
      val header = flattened.headers(i).erase
      val input  = inputs(i)

      // TODO: Fix this - temporary solution
      val value = header.textCodec.swap.map(_.encode(input)).toOption.getOrElse("")

      headers = headers ++ Headers(header.name, value)

      i = i + 1
    }

    headers
  }

  private def encodeMethod(inputs: Array[Any]): Option[zio.http.model.Method] = {
    if (flattened.methods.nonEmpty) {
      val method = flattened.methods.head.erase
      Some(zio.http.model.Method.fromString(method.encode(inputs(0))))
    } else {
      None
    }
  }

  private def encodeBody(inputs: Array[Any]): Body = {
    if (jsonEncoders.length == 0) Body.empty
    else if (jsonEncoders.length == 1) {
      val encoder = jsonEncoders(0)

      encoder(inputs(0))
    } else throw new IllegalStateException("A request on a REST endpoint should have at most one body")
  }
}
