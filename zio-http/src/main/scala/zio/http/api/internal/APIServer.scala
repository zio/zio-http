package zio.http.api.internal

import zio._
import zio.http._
import zio.http.api._
import zio.http.api.internal.Mechanic.Constructor
import zio.http.model.Headers
import zio.schema._
import zio.schema.codec._
import zio.stacktracer.TracingImplicits.disableAutoTrace // scalafix:ok;

private[api] final case class APIServer[R, E, I, O](handledApi: Service.HandledAPI[R, E, I, O, _]) {
  private val api     = handledApi.api
  private val handler = handledApi.handler

  private val optionSchema: Option[Schema[Any]] = api.input.bodySchema.map(_.asInstanceOf[Schema[Any]])
  private val bodyJsonDecoder: Chunk[Byte] => Either[String, Any] =
    JsonCodec.decode(optionSchema.getOrElse(Schema[Unit].asInstanceOf[Schema[Any]]))
  private val outputJsonEncoder: Any => Chunk[Byte]               =
    JsonCodec.encode(api.output.bodySchema.asInstanceOf[Schema[Any]])

  private val constructor: Constructor[I]        = Mechanic.makeConstructor(api.input)
  private val flattened: Mechanic.FlattenedAtoms = Mechanic.flatten(api.input)

  private val hasOutput = api.output != Out.unit

  def handle(routeInputs: Chunk[Any], request: Request)(implicit trace: Trace): ZIO[R, E, Response] = {
    val inputsBuilder = flattened.makeInputsBuilder()

    // TODO: Bounds checking
    java.lang.System.arraycopy(routeInputs.toArray, 0, inputsBuilder.routes, 0, routeInputs.length)

    decodeQuery(request.url.queryParams, inputsBuilder.queries)
    decodeHeaders(request.headers, inputsBuilder.headers)

    decodeBody(request.body, inputsBuilder.inputBodies) *> {
      val input: I = constructor(inputsBuilder)

      handler(input).map { output =>
        val body =
          if (hasOutput) Body.fromChunk(outputJsonEncoder(output))
          else Body.empty
        Response(body = body)
      }
    }
  }

  private def decodeQuery(queryParams: QueryParams, inputs: Array[Any]): Unit = {
    var i = 0
    while (i < flattened.queries.length) {
      val query = flattened.queries(i).asInstanceOf[In.Query[Any]]

      val value = queryParams
        .getOrElse(query.name, Nil)
        .headOption
        .getOrElse(throw APIError.MissingQueryParam(query.name))

      inputs(i) =
        query.textCodec.decode(value).getOrElse(throw APIError.MalformedQueryParam(query.name, query.textCodec))

      i = i + 1
    }
  }

  private def decodeHeaders(headers: Headers, inputs: Array[Any]): Unit = {
    var i = 0
    while (i < flattened.headers.length) {
      val header = flattened.headers(i).asInstanceOf[In.Header[Any]]

      val value = headers.get(header.name).getOrElse(throw APIError.MissingHeader(header.name))

      inputs(i) =
        header.textCodec.decode(value).getOrElse(throw APIError.MalformedHeader(header.name, header.textCodec))

      i = i + 1
    }
  }

  private def decodeBody(body: Body, inputs: Array[Any])(implicit trace: Trace): UIO[Unit] =
    if (inputs.isEmpty)
      ZIO.unit
    else
      body.asChunk.orDie.map { chunk =>
        inputs(0) = bodyJsonDecoder(chunk).getOrElse(throw APIError.MalformedRequestBody(api))
      }
}
