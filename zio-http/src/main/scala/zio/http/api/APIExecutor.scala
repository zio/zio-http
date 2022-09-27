package zio.http.api

import zio._
import zio.http._
import zio.http.api.internal.APIClient
import zio.stacktracer.TracingImplicits.disableAutoTrace // scalafix:ok;

/**
 * A [[zio.http.api.APIExecutor]] is responsible for taking a service
 * invocation, and executing this invocation, returning the final result, or
 * failing with some kind of RPC error.
 */
trait APIExecutor[+Ids] {
  def apply[Id, A, B](invocation: Invocation[Id, A, B])(implicit ev: Ids <:< Id, trace: Trace): ZIO[Any, Throwable, B]
}

object APIExecutor {

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
    val metadata = zio.http.api.internal.Memoized[API[_, _], APIClient[Any, Any]] { (api: API[_, _]) =>
      APIClient(
        locator.locate(api).getOrElse(throw APIError.NotFound(s"Could not locate API", api)),
        api.asInstanceOf[API[Any, Any]],
      )
    }

    def apply[Id, A, B](
      invocation: Invocation[Id, A, B],
    )(implicit ev: Nothing <:< Id, trace: Trace): ZIO[Any, Throwable, B] = {
      val executor = metadata.get(invocation.api).asInstanceOf[APIClient[A, B]]

      executor.execute(client, invocation.input).asInstanceOf[ZIO[Any, Throwable, B]]
    }
  }
}
