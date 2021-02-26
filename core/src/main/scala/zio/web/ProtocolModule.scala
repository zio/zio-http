package zio.web

import java.io.IOException

import zio.{ Has, Tag, ZLayer }
import zio.blocking.Blocking
import zio.logging.Logging

trait ProtocolModule {
  type ServerConfig
  type ClientConfig
  type ServerService
  type ProtocolDocs
  type Middleware[-R, +E]
  type MinMetadata[+_]

  def makeServer[M[+_] <: MinMetadata[_], R <: Has[ServerConfig]: Tag, E, Ids: Tag](
    middleware: Middleware[R, E],
    endpoints: Endpoints[M, Ids],
    handlers: Handlers[M, R, Ids]
  ): ZLayer[R with Blocking with Logging, IOException, Has[ServerService]]

  def makeDocs[R, M[+_] <: MinMetadata[_]](endpoints: Endpoints[M, _]): ProtocolDocs

  def makeClient[M[+_] <: MinMetadata[_], Ids](
    endpoints: Endpoints[M, Ids]
  ): ZLayer[Has[ClientConfig], IOException, Has[ClientService[Ids]]] = ???
}
