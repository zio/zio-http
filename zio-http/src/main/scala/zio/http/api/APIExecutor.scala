package zio.http.api

import zio._
import zio.http._
import zio.http.api.internal.EndpointClient
import zio.stacktracer.TracingImplicits.disableAutoTrace // scalafix:ok;

/**
 * A [[zio.http.api.APIExecutor]] is responsible for taking a service
 * invocation, and executing this invocation, returning the final result, or
 * failing with some kind of RPC error.
 */
trait APIExecutor[+MI, +MO, +Ids] { self =>
  def apply[Id, A, B](
    invocation: Invocation[Id, A, B],
  )(implicit ev: Ids <:< Id, trace: Trace): ZIO[Any, Throwable, B]

  def middlewareInput(implicit trace: Trace): Task[MI]

  def mapMiddlewareInput[MI2](f: MI => MI2): APIExecutor[MI2, MO, Ids] =
    new APIExecutor[MI2, MO, Ids] {
      def apply[Id, A, B](
        invocation: Invocation[Id, A, B],
      )(implicit ev: Ids <:< Id, trace: Trace): ZIO[Any, Throwable, B] =
        self.apply(invocation)

      def middlewareInput(implicit trace: Trace): Task[MI2] = self.middlewareInput.map(f)
    }
}

object APIExecutor {

  /**
   * The default constructor creates a typed executor, which requires a service
   * registry, which keeps track of the locations of all services.
   */
  def apply[MI, MO, Ids](
    client: Client,
    registry: EndpointRegistry[MI, MO, Ids],
    mi: Task[MI],
  ): APIExecutor[Any, Any, Ids] =
    UntypedServiceExecutor(client, registry, mi)

  /**
   * An alternate constructor can be used to create an untyped executor, which
   * can attempt to execute any service, and which may fail at runtime if it
   * does not know the location of a service.
   */
  def untyped(client: Client, locator: APILocator): APIExecutor[Any, Any, Nothing] =
    UntypedServiceExecutor(client, locator, ZIO.unit)

  private final case class UntypedServiceExecutor[MI](client: Client, locator: APILocator, middlewareInput0: Task[MI])
      extends APIExecutor[MI, Any, Nothing] {
    val metadata = zio.http.api.internal.Memoized[EndpointSpec[_, _], EndpointClient[Any, Any]] {
      (api: EndpointSpec[_, _]) =>
        EndpointClient(
          locator.locate(api).getOrElse(throw APIError.NotFound(s"Could not locate API", api)),
          api.asInstanceOf[EndpointSpec[Any, Any]],
        )
    }

    def apply[Id, A, B](
      invocation: Invocation[Id, A, B],
    )(implicit ev: Nothing <:< Id, trace: Trace): ZIO[Any, Throwable, B] = {
      val executor = metadata.get(invocation.api).asInstanceOf[EndpointClient[A, B]]

      executor.execute(client, invocation.input).asInstanceOf[ZIO[Any, Throwable, B]]
    }

    def middlewareInput(implicit trace: Trace): Task[MI] = middlewareInput0
  }
}
