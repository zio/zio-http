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
  type MinMetadata[+A]    = HttpAnn[A]

  val defaultProtocol: codec.Codec

  val allProtocols: Map[String, codec.Codec]

  def makeServer[M[+_] <: MinMetadata[_], R <: Has[ServerConfig], E, Identities](
    middleware: Middleware[R, E],
    endpoints: Endpoints[M, Identities],
    handlers: Handlers[M, R, Identities]
  ): ZLayer[R with Blocking with Logging, IOException, HttpRouter with ServerService] =
    ZLayer.requires[R with Blocking with Logging] >+>
      HttpRouter.basic(endpoints) >+>
      ZLayer.fromServiceManaged { config: ServerConfig =>
        HttpServer.build(config, endpoints.asInstanceOf)
      }

  override def makeDocs[R, M[+_] <: MinMetadata[_]](endpoints: Endpoints[M, _]): ProtocolDocs =
    ???
}
