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

    sealed case class Cached(
      encoder: Any => Chunk[Byte],
      decoder: Chunk[Byte] => Either[String, Any],
      requestHeaders: Headers,
    )

    val cached = zio.http.api.internal.APIMetadata[Cached] { (api: API[_, _]) =>
      val optionSchema: Option[Schema[Any]] = api.input.bodySchema.map(_.asInstanceOf[Schema[Any]])
      val encoder: Any => Chunk[Byte] = JsonCodec.encode(optionSchema.getOrElse(Schema[Unit].asInstanceOf[Schema[Any]]))
      val decoder: Chunk[Byte] => Either[String, Any] =
        JsonCodec.decode(api.output.bodySchema.asInstanceOf[Schema[Any]])
      val headers: Headers                            = Headers.contentType("application/json")

      Cached(encoder, decoder, headers)
    }

    def apply[Id, A, B](invocation: Invocation[Id, A, B])(implicit ev: Nothing <:< Id): ZIO[Any, Throwable, B] = {
      val location = locator.locate(invocation.api)

      location match {
        case Some(loc) => execute(loc, invocation)
        case None => ZIO.die(NotFound("An API could not be located during execution of an invocation", invocation.api))
      }
    }

    def execute[Id, A, B](loc: URL, invocation: Invocation[Id, A, B]): ZIO[Any, Throwable, B] = {
      val meta = cached.get(invocation.api)

      val encodedInput: Chunk[Byte] = meta.encoder(invocation.request)

      // FIXME: Add query string parameters, path, headers:

      client.request(Request(url = loc, headers = meta.requestHeaders, body = Body.fromChunk(encodedInput))).flatMap {
        response =>
          response.body.asChunk.flatMap { response =>
            meta.decoder(response) match {
              case Left(error)  => ZIO.die(DecodeError(s"Could not decode response: $error", invocation.api))
              case Right(value) => ZIO.succeed(value.asInstanceOf[B])
            }
          }
      }
    }
  }
}
