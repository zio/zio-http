package zio.web

import java.io.IOException

import zio.{ Has, /*Task,*/ ZLayer }
import zio.blocking.Blocking
import zio.logging.Logging

trait ProtocolModule {
  type ServerConfig
  type ClientConfig
  type ServerService
  type ProtocolDocs
  type Middleware[-R, +E]
  type MinMetadata[+_]

  // TODO: require implicit evidence that all Endpoints have handlers
  def makeServer[M[+_] <: MinMetadata[_], R <: Has[ServerConfig], E, Identities](
    middleware: Middleware[R, E],
    endpoints: Endpoints[M, Identities],
    handlers: Handlers[M, R, Identities]
  ): ZLayer[R with Blocking with Logging, IOException, ServerService]

  def makeDocs[R, M[+_] <: MinMetadata[_]](endpoints: Endpoints[M, _]): ProtocolDocs

  def makeClient[M[+_] <: MinMetadata[_], Identities](
    endpoints: Endpoints[M, Identities]
  ): ZLayer[Has[ClientConfig], IOException, Has[ClientService[Identities]]] = ???
}
