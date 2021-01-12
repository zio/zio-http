package zio.web.http

import zio._
import zio.web.http.model._
import zio.web._

import java.io.IOException

trait HttpProtocolModule extends ProtocolModule {
  type ServerConfig       = HttpServerConfig
  type ClientConfig       = HttpClientConfig
  type ServerService      = Any
  type Middleware[-R, +E] = HttpMiddleware[R, E]
  type MinMetadata        = Any
  type MaxMetadata        = Route with Method

  val defaultProtocol: codec.Codec

  val allProtocols: Map[String, codec.Codec]

  override def makeServer[M >: MaxMetadata <: MinMetadata, R <: Has[ServerConfig], E, A](
    middleware: Middleware[R, E],
    endpoints: Endpoints[M, A]
  ): ZLayer[R, IOException, Has[ServerService]] = ???

  override def makeDocs[M >: MaxMetadata <: MinMetadata](endpoints: Endpoints[M, _]): ProtocolDocs =
    ???

  override def makeClient[M >: MaxMetadata <: MinMetadata, A](
    endpoints: Endpoints[M, A]
  ): ZLayer[Has[ClientConfig], IOException, Has[ClientService[A]]] = ???
}
