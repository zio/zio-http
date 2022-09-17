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
    import zio.schema._
    import zio.schema.codec._
    import zio.http.api.internal.Mechanic

    sealed case class Cached(
      url: URL,
      encoder: Encoder,
      decoder: Chunk[Byte] => Either[String, Any],
      deconstructor: Mechanic.Deconstructor[Any],
    )

    trait Encoder {
      def encodeRoute(inputs: Array[Any]): Path

      def encodeQuery(inputs: Array[Any]): Map[String, List[String]]

      def encodeHeaders(inputs: Array[Any]): Headers

      def encodeBody(inputs: Array[Any]): Body
    }

    val metadata = zio.http.api.internal.APIMetadata[Cached] { (api: API[_, _]) =>
      val optionSchema: Option[Schema[Any]]                 = api.input.bodySchema.map(_.asInstanceOf[Schema[Any]])
      val inputEncoder: Any => Chunk[Byte]                  =
        JsonCodec.encode(optionSchema.getOrElse(Schema[Unit].asInstanceOf[Schema[Any]]))
      val outputDecoder: Chunk[Byte] => Either[String, Any] =
        JsonCodec.decode(api.output.bodySchema.asInstanceOf[Schema[Any]])
      val deconstructor = Mechanic.makeDeconstructor(api.input).asInstanceOf[Mechanic.Deconstructor[Any]]
      val flattened     = Mechanic.flatten(api.input)

      val encoder =
        new Encoder {
          def encodeRoute(inputs: Array[Any]): Path = {
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

          def encodeQuery(inputs: Array[Any]): Map[String, List[String]] = {
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

          def encodeHeaders(inputs: Array[Any]): Headers = {
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

          def encodeBody(inputs: Array[Any]): Body = Body.fromChunk(inputEncoder(inputs(0)))
        }

      val url = locator.locate(api).getOrElse(throw NotFound(s"Could not locate API", api))

      Cached(url, encoder, outputDecoder, deconstructor)
    }

    def apply[Id, A, B](invocation: Invocation[Id, A, B])(implicit ev: Nothing <:< Id): ZIO[Any, Throwable, B] = {
      val location = locator.locate(invocation.api)

      location match {
        case Some(loc) => execute(loc, invocation)
        case None => ZIO.die(NotFound("An API could not be located during execution of an invocation", invocation.api))
      }
    }

    def execute[Id, A, B](loc: URL, invocation: Invocation[Id, A, B]): ZIO[Any, Throwable, B] = {
      val cached = metadata.get(invocation.api)

      val inputs = cached.deconstructor(invocation.input)

      val encoder = cached.encoder

      val route   = encoder.encodeRoute(inputs.routes)
      val query   = encoder.encodeQuery(inputs.queries)
      val headers = encoder.encodeHeaders(inputs.headers)
      val body    = encoder.encodeBody(inputs.inputBodies)

      val request = Request(
        method = invocation.api.method,
        url = cached.url ++ URL(route, URL.Location.Relative, query),
        headers = headers,
        body = body,
      )

      client.request(request).flatMap { response =>
        response.body.asChunk.flatMap { response =>
          cached.decoder(response) match {
            case Left(error)  => ZIO.die(DecodeError(s"Could not decode response: $error", invocation.api))
            case Right(value) => ZIO.succeed(value.asInstanceOf[B])
          }
        }
      }
    }
  }
}
