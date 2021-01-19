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
  type MaxMetadata

  // TODO: require implicit evidence that all Endpoints have handlers 
  def makeServer[M >: MaxMetadata <: MinMetadata, R <: Has[ServerConfig], E](
    middleware: Middleware[R, E],
    endpoints: Endpoints
  ): ZLayer[R with Blocking with Logging, IOException, ServerService]

  def makeDocs[R, M >: MaxMetadata <: MinMetadata](endpoints: Endpoints): ProtocolDocs

  // def makeClient[R, M >: MaxMetadata <: MinMetadata](
  //   endpoints: Endpoints[R, M, A]
  // ): ZLayer[Has[ClientConfig], IOException, Has[ClientService[A]]]

  // trait ClientService[A] {
  //   def invoke[M, I, O, R, H](endpoint: Endpoint[M, I, O, R, H], request: I)(
  //     implicit ev: A <:< Endpoint[M, I, O, R, H]
  //   ): Task[O]
  // }
}
