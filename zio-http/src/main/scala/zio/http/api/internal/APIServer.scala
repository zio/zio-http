package zio.http.api.internal

import zio._
import zio.http._
import zio.http.api._

import zio.schema._
import zio.schema.codec._

private[api] final case class APIServer[R, E, I, O](handledApi: Service.HandledAPI[R, E, I, O]) {
  private val api     = handledApi.api
  private val handler = handledApi.handler

  private val optionSchema: Option[Schema[Any]] = api.input.bodySchema.map(_.asInstanceOf[Schema[Any]])
  private val bodyJsonDecoder: Chunk[Byte] => Either[String, Any] =
    JsonCodec.decode(optionSchema.getOrElse(Schema[Unit].asInstanceOf[Schema[Any]]))
  private val outputJsonEncoder: Any => Chunk[Byte]               =
    JsonCodec.encode(api.output.bodySchema.asInstanceOf[Schema[Any]])
  private val constructor = Mechanic.makeConstructor(api.input).asInstanceOf[Mechanic.Constructor[I]]
  private val flattened   = Mechanic.flatten(api.input)

  def handle(routeInputs: Chunk[Any], request: Request): ZIO[R, E, Response] = {
    val inputsBuilder = flattened.makeInputsBuilder()

    // TODO: Bounds checking
    java.lang.System.arraycopy(routeInputs.toArray, 0, inputsBuilder.routes, 0, routeInputs.length)

    decodeQuery(request.url.queryParams, inputsBuilder.queries)
    decodeHeaders(request.headers, inputsBuilder.headers)

    decodeBody(request.body, inputsBuilder.inputBodies).flatMap { _ =>
      val input: I = constructor(inputsBuilder)

      handler(input).map { output =>
        val body = outputJsonEncoder(output)
        Response(body = Body.fromChunk(body))
      }
    }
  }

  // def decodeRoute(path: Path, inputs: Array[Any]): Unit = {
  //   val segments = path.textSegments
  //   if (segments.length < inputs.length) throw new Exception("Insufficient route length")
  //   var i = 0
  //   while (i < flattened.routes.length) {
  //     val route = flattened.routes(i).asInstanceOf[In.Route[Any]]

  //     inputs(i) = route.textCodec.decode(segments(i)).getOrElse(throw new Exception(s"Error decoding route segment ${route.textCodec}"))

  //     i = i + 1
  //   }
  // }

  private def decodeQuery(queryParams: Map[String, List[String]], inputs: Array[Any]): Unit = {
    var i = 0
    while (i < flattened.queries.length) {
      val query = flattened.queries(i).asInstanceOf[In.Query[Any]]

      val value = queryParams
        .getOrElse(query.name, Nil)
        .headOption
        .getOrElse(throw new Exception(s"Missing query parameter ${query.name}"))

      inputs(i) =
        query.textCodec.decode(value).getOrElse(throw new Exception(s"Error decoding query parameter ${query.name}"))

      i = i + 1
    }
  }

  private def decodeHeaders(headers: Headers, inputs: Array[Any]): Unit = {
    var i = 0
    while (i < flattened.headers.length) {
      val header = flattened.headers(i).asInstanceOf[In.Header[Any]]

      val value = headers.get(header.name).getOrElse(throw new Exception(s"Missing header ${header.name}"))

      inputs(i) = header.textCodec.decode(value).getOrElse(throw new Exception(s"Error decoding header ${header.name}"))

      i = i + 1
    }
  }

  private def decodeBody(body: Body, inputs: Array[Any]): UIO[Unit] =
    body.asChunk.orDie.map { chunk =>
      if (inputs.length == 0) ()
      else {
        inputs(0) = bodyJsonDecoder(chunk).getOrElse(throw new Exception(s"Error decoding body"))
      }
    }
}
