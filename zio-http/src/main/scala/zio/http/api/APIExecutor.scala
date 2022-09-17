package zio.http.api

import zio._
import zio.http._

/**
 * A [[zio.http.api.APIExecutor]] is responsible for taking a service
 * invocation, and executing this invocation, returning the final result, or
 * failing with some kind of RPC error.
 */
trait APIExecutor[+Ids] {
  def apply[Id, A, B](invocation: Invocation[Id, A, B])(implicit ev: Ids <:< Id): ZIO[Any, Throwable, B]
}
object APIExecutor      {
  final case class NotFound(message: String, api: API[_, _])    extends RuntimeException(message)
  final case class DecodeError(message: String, api: API[_, _]) extends RuntimeException(message)

  /**
   * The default constructor creates a typed executor, which requires a service
   * registry, which keeps track of the locations of all services.
   */
  def apply[Ids](client: Client, registry: APIRegistry[Ids]): APIExecutor[Ids] =
    untyped(client, registry)

  /**
   * An alternate constructor can be used to create an untyped executor, which
   * can attempt to execute any service, and which may fail at runtime if it
   * does not know the location of a service.
   */
  def untyped(client: Client, locator: APILocator): APIExecutor[Nothing] =
    UntypedServiceExecutor(client, locator)

  private final case class UntypedServiceExecutor(client: Client, locator: APILocator) extends APIExecutor[Nothing] {
    val metadata = zio.http.api.internal.APIMetadata[CompiledExecutor[Any, Any]] { (api: API[_, _]) =>
      CompiledExecutor(
        locator.locate(api).getOrElse(throw NotFound(s"Could not locate API", api)),
        api.asInstanceOf[API[Any, Any]],
      )
    }

    def apply[Id, A, B](invocation: Invocation[Id, A, B])(implicit ev: Nothing <:< Id): ZIO[Any, Throwable, B] = {
      val executor = metadata.get(invocation.api).asInstanceOf[CompiledExecutor[A, B]]

      executor.execute(client, invocation.input).asInstanceOf[ZIO[Any, Throwable, B]]
    }
  }

  private[api] final case class CompiledExecutor[I, O](apiRoot: URL, api: API[I, O]) {
    import zio.schema._
    import zio.schema.codec._
    import zio.http.api.internal.Mechanic

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
        val route = flattened.routes(i).asInstanceOf[In.Route[Any]]
        val input = inputs(i)

        val segment = route.textCodec.encode(input)

        path = path / segment
        i = i + 1
      }

      path
    }

    private def encodeQuery(inputs: Array[Any]): Map[String, List[String]] = {
      var queryParams = Map.empty[String, List[String]]

      var i = 0
      while (i < inputs.length) {
        val query = flattened.queries(i).asInstanceOf[In.Query[Any]]
        val input = inputs(i)

        val value = query.textCodec.encode(input)

        queryParams =
          if (queryParams.contains(query.name)) queryParams.updated(query.name, value :: queryParams(query.name))
          else queryParams.updated(query.name, value :: Nil)

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

    def execute(client: Client, input: I): ZIO[Any, Throwable, O] = {
      val inputs = deconstructor(input)

      val route   = encodeRoute(inputs.routes)
      val query   = encodeQuery(inputs.queries)
      val headers = encodeHeaders(inputs.headers)
      val body    = encodeBody(inputs.inputBodies)

      val request = Request(
        method = api.method,
        url = apiRoot ++ URL(route, URL.Location.Relative, query),
        headers = headers,
        body = body,
      )

      client.request(request).flatMap { response =>
        response.body.asChunk.flatMap { response =>
          outputJsonDecoder(response) match {
            case Left(error)  => ZIO.die(DecodeError(s"Could not decode response: $error", api))
            case Right(value) => ZIO.succeed(value.asInstanceOf[O])
          }
        }
      }
    }
  }
}
