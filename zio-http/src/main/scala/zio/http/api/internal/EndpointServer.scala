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

  private val inputCodecs        = BodyCodec.findAll(api.input)
  private val outputCodecs       = BodyCodec.findAll(api.output)
  private val inputJsonDecoders  = inputCodecs.map(bodyCodec => bodyCodec.decodeFromBody(_, JsonCodec))
  private val outputJsonEncoders = outputCodecs.map(bodyCodec => bodyCodec.erase.encodeToBody(_, JsonCodec))

  private val constructor: Constructor[I]        = Mechanic.makeConstructor(api.input)
  private val flattened: Mechanic.FlattenedAtoms = Mechanic.flatten(api.input)

  def handle(routeInputs: Chunk[Any], request: Request)(implicit trace: Trace): ZIO[R, E, Response] = {
    val inputsBuilder = flattened.makeInputsBuilder()

    // TODO: Bounds checking
    java.lang.System.arraycopy(routeInputs.toArray, 0, inputsBuilder.routes, 0, routeInputs.length)

    decodeQuery(request.url.queryParams, inputsBuilder.queries)
    decodeHeaders(request.headers, inputsBuilder.headers)

    decodeBody(request.body, inputsBuilder.bodies).orDie *> {
      val input: I = constructor(inputsBuilder)

      handler(input).map { output =>
        val body =
          if (outputJsonEncoders.nonEmpty) outputJsonEncoders(0)(output)
          else Body.empty
        Response(body = body)
      }
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
    if (inputJsonDecoders.length == 0) ZIO.unit
    else if (inputJsonDecoders.length == 1) {
      val decoder = inputJsonDecoders(0)

      decoder(body).map { result => inputs(0) = result }
    } else {
      ZIO.foreachDiscard(inputJsonDecoders.zipWithIndex) { case (decoder, index) =>
        decoder(body).map { result => inputs(index) = result }
      }
    }
}
