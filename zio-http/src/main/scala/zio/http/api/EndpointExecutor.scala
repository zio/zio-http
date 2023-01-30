package zio.http.api

import zio._
import zio.http._
import zio.http.api.internal.EndpointClient
import zio.stacktracer.TracingImplicits.disableAutoTrace // scalafix:ok;

/**
 * A [[zio.http.api.EndpointExecutor]] is responsible for taking an endpoint
 * invocation, and executing this invocation, returning the final result, or
 * failing with some kind of RPC error.
 */
trait EndpointExecutor[+Ids, +MI, +ME] { self =>
  def apply[Id, A, E, B, ME1 >: ME](
    invocation: Invocation[Id, A, E, B],
  )(implicit ev: Ids <:< Id, alt: Alternator[E, ME1], trace: Trace): ZIO[Any, alt.Out, B]

  def middlewareInput(implicit trace: Trace): Task[MI]

  def mapMiddlewareInput[MI2](f: MI => MI2): EndpointExecutor[Ids, MI2, ME] =
    new EndpointExecutor[Ids, MI2, ME] {
      def apply[Id, A, E, B, ME1 >: ME](
        invocation: Invocation[Id, A, E, B],
      )(implicit ev: Ids <:< Id, alt: Alternator[E, ME1], trace: Trace): ZIO[Any, alt.Out, B] =
        self.apply[Id, A, E, B, ME1](invocation)

      def middlewareInput(implicit trace: Trace): Task[MI2] = self.middlewareInput.map(f)
    }
}

object EndpointExecutor {

  /**
   * The default constructor creates a typed executor, which requires a service
   * registry, which keeps track of the locations of all services.
   */
  def apply[Ids, MI, ME](
    client: Client,
    registry: EndpointRegistry[Ids, MI, ME],
    mi: Task[MI],
  ): EndpointExecutor[Ids, MI, ME] =
    UntypedServiceExecutor(client, registry, mi)

  /**
   * An alternate constructor can be used to create an untyped executor, which
   * can attempt to execute any service, and which may fail at runtime if it
   * does not know the location of a service.
   */
  def untyped(client: Client, locator: EndpointLocator): EndpointExecutor[Nothing, Any, Nothing] =
    UntypedServiceExecutor(client, locator, ZIO.unit)

  private final case class UntypedServiceExecutor[MI](
    client: Client,
    locator: EndpointLocator,
    middlewareInput0: Task[MI],
  ) extends EndpointExecutor[Nothing, MI, Nothing] {
    val metadata = zio.http.api.internal.Memoized[EndpointSpec[_, _, _], EndpointClient[Any, Any, Any]] {
      (api: EndpointSpec[_, _, _]) =>
        EndpointClient(
          locator.locate(api).getOrElse(throw EndpointError.NotFound(s"Could not locate API", api)),
          api.asInstanceOf[EndpointSpec[Any, Any, Any]],
        )
    }

    def apply[Id, A, E, B, ME1 >: Nothing](
        invocation: Invocation[Id, A, E, B],
      )(implicit ev: Nothing <:< Id, alt: Alternator[E, ME1], trace: Trace): ZIO[Any, alt.Out, B] = {
      val executor = metadata.get(invocation.api)

      executor.execute(client, invocation.input).asInstanceOf[ZIO[Any, alt.Out, B]]
    }

    def middlewareInput(implicit trace: Trace): Task[MI] = middlewareInput0
  }
}
