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
  type MinMetadata

  // TODO: require implicit evidence that all Endpoints have handlers
  def makeServer[M <: MinMetadata, R <: Has[ServerConfig], E](
    middleware: Middleware[R, E],
    endpoints: Endpoints[M, _]
  ): ZLayer[R with Blocking with Logging, IOException, ServerService]

  def makeDocs[R, M <: MinMetadata](endpoints: Endpoints[M, _]): ProtocolDocs

  def makeClient[M <: MinMetadata, Identities](
    endpoints: Endpoints[M, Identities]
  ): ZLayer[Has[ClientConfig], IOException, Has[ClientService[Identities]]] = ???
}
