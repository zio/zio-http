package zio.web.websockets

import java.io.IOException

import zio.{ Has, ZLayer }
import zio.web.ProtocolModule

trait WebSocketsProtocolModule extends ProtocolModule {
  type ServerConfig       = WebSocketsServerConfig
  type ClientConfig       = WebSocketsClientConfig
  type ServerService      = Any
  type ProtocolDocs       = Any
  type Middleware[-R, +E] = WebSocketsMiddleware[R, E]
  type MinMetadata        = Any
  type MaxMetadata        = Any

  def makeServer[M >: MaxMetadata <: MinMetadata, R <: Has[ServerConfig], E, A](
    middleware: Middleware[R, E],
    endpoints: Endpoints[M, A]
  ): ZLayer[R, IOException, Has[ServerService]] = ???

  def makeDocs[M >: MaxMetadata <: MinMetadata](endpoints: Endpoints[M, _]): ProtocolDocs = ???

  def makeClient[M >: MaxMetadata <: MinMetadata, A](
    endpoints: Endpoints[M, A]
  ): ZLayer[Has[ClientConfig], IOException, Has[ClientService[A]]] = ???
}
