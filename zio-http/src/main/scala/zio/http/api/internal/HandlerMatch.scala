package zio.http.api.internal

import zio._
import zio.http.api._
import zio.http.{Request, Response}

final case class HandlerMatch[-R, +E, Input, Output](
  handledApi: Service.HandledAPI[R, E, Input, Output],
  // route parsing results
  results: Chunk[Any],
) {

  val in       = handledApi.api.input
  val atoms    = Mechanic.flatten(in)
  val threader = Mechanic.makeConstructor(in)

  // TODO: precompute threader and anything else that's derivable from API itself
  def run(request: Request): ZIO[R, E, Response] = {
    // All Results
    val queryResults: Chunk[Any] = parseQuery(request, atoms.queries)

    // Reassembled and Threaded
    val fullResults = Mechanic.InputResults(results, queryResults)
    val _           = fullResults
    val input       = threader(fullResults)
    handledApi.handler(input).map { out =>
      Response.text(out.toString)
    }
  }

  def parseQuery(request: Request, queryAtoms: Chunk[In.Query[_]]): Chunk[Any] = {
    queryAtoms.map { queryAtom =>
      request.url.queryParams(queryAtom.name).head
    }
  }
}

private[api] final case class CompiledExecutor[I, O](api: API[I, O]) {
  import zio.http._
  import zio.schema._
  import zio.schema.codec._
  import zio.http.api.internal.Mechanic

  private val optionSchema: Option[Schema[Any]]            = api.input.bodySchema.map(_.asInstanceOf[Schema[Any]])
  val inputJsonDecoder: Chunk[Byte] => Either[String, Any] =
    JsonCodec.decode(optionSchema.getOrElse(Schema[Unit].asInstanceOf[Schema[Any]]))
  val outputJsonEncoder: Any => Chunk[Byte]                =
    JsonCodec.encode(api.output.bodySchema.asInstanceOf[Schema[Any]])
  val constructor       = Mechanic.makeConstructor(api.input).asInstanceOf[Mechanic.Constructor[Any]]
  private val flattened = Mechanic.flatten(api.input)

  def decodeRoute(path: Path, inputs: Array[Any]): Unit = {
    var i = 0
    while (i < flattened.routes.length) {
      val route = flattened.routes(i).asInstanceOf[In.Route[Any]]

      val _ = route

      i = i + 1
    }
  }

  def decodeQuery(queryParams: Map[String, List[String]], inputs: Array[Any]): Unit = {
    var i = 0
    while (i < flattened.queries.length) {
      val query = flattened.queries(i).asInstanceOf[In.Query[Any]]
      val input = inputs(i)

      val _ = query
      val _ = input

      i = i + 1
    }
  }

  def decodeHeaders(headers: Headers, inputs: Array[Any]): Unit = {
    var i = 0
    while (i < flattened.headers.length) {
      val header = flattened.headers(i).asInstanceOf[In.Header[Any]]
      val input  = inputs(i)

      val _ = header
      val _ = input

      i = i + 1
    }
  }

  def decodeBody(body: Body, inputs: Array[Any]): Unit =
    ???

  def execute(request: Request): ZIO[Any, Throwable, Response] = {
    ???
  }
}
