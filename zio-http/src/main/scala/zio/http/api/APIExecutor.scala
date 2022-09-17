package zio.http.api

import zio.ZIO
import zio.http.Client

/**
 * A [[zio.http.api.APIExecutor]] is responsible for taking a service
 * invocation, and executing this invocation, returning the final result, or
 * failing with some kind of RPC error.
 */
trait APIExecutor[+Ids] {
  def apply[Id, A, B](invocation: Invocation[Id, A, B])(implicit ev: Ids <:< Id): ZIO[Any, Throwable, B]
}
object APIExecutor      {

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
    def apply[Id, A, B](invocation: Invocation[Id, A, B])(implicit ev: Nothing <:< Id): ZIO[Any, Throwable, B] = ???
  }
}
