package zio.web.http

import zio._
import zio.blocking.Blocking
import zio.logging.Logging
import zio.web.http.model._
import zio.web._
import zio.web.http.internal.HttpRouter

import java.io.IOException

trait HttpProtocolModule extends ProtocolModule {
  type ServerConfig       = HttpServerConfig
  type ClientConfig       = HttpClientConfig
  type ServerService      = Has[HttpServer]
  type Middleware[-R, +E] = HttpMiddleware[R, E]
  type MinMetadata        = Route with Method
  type MaxMetadata        = Nothing

  val defaultProtocol: codec.Codec

  val allProtocols: Map[String, codec.Codec]

  override def makeServer[M >: MaxMetadata <: MinMetadata, R <: Has[ServerConfig], E](
    middleware: Middleware[R, E],
    endpoints: Endpoints[M, _]
  ): ZLayer[R with Blocking with Logging, IOException, HttpRouter with ServerService] =
    ZLayer.requires[R with Blocking with Logging] >+>
      HttpRouter.basic(endpoints) >+>
      ZLayer.fromServiceManaged { config: ServerConfig =>
        HttpServer.build(config, endpoints)
      }

  def makeServerOps[M >: MaxMetadata <: MinMetadata, R <: Has[ServerConfig], E](
    middleware: Middleware[R, E],
    endpoints: Endpoints[M, _]
  ): ZLayer[R with Blocking with Logging, IOException, ServerService] = ???

  override def makeDocs[R, M >: MaxMetadata <: MinMetadata](endpoints: Endpoints[M, _]): ProtocolDocs =
    ???

  // override def makeClient[R, M >: MaxMetadata <: MinMetadata](
  //   endpoints: Endpoints[R, M]
  // ): ZLayer[Has[ClientConfig], IOException, Has[ClientService[A]]] = ???
}
