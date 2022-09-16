package zio.http.api

import zio.ZIO
import zio.http.Client

final case class ServiceExecutor[Ids](client: Client, registry: ServiceRegistry[Ids]) {
  def apply[Id, A, B](invocation: Invocation[Id, A, B])(implicit ev: Ids <:< Id): ZIO[Any, Throwable, B] = ???
}
