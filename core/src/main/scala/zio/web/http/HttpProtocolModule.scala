package zio.web.http

import zio._
import zio.blocking.Blocking
import zio.clock.Clock
import zio.logging.Logging
import zio.web.http.model._
import zio.web._

import java.io.IOException

trait HttpProtocolModule extends ProtocolModule {
  type ServerConfig       = HttpServerConfig
  type ClientConfig       = HttpClientConfig
  type ServerService      = HttpServer
  type ClientService[Ids] = HttpClient[Ids]
  type Middleware[-R, +E] = HttpMiddleware[R, E]
  type MinMetadata[+A]    = HttpAnn[A]

  val defaultProtocol: codec.Codec

  val allProtocols: Map[String, codec.Codec]

  override def makeServer[M[+_] <: MinMetadata[_], R <: Has[ServerConfig]: Tag, E, Ids: Tag](
    middleware: Middleware[R, E],
    endpoints: Endpoints[M, Ids],
    handlers: Handlers[M, R, Ids]
  ): ZLayer[R with Blocking with Logging, IOException, Has[ServerService]] = {
    val _ = middleware

    ZLayer.fromManaged(
      for {
        config <- ZIO.service[ServerConfig].toManaged_
        env    <- ZIO.environment[R].toManaged_
        server <- HttpServer.build(config, endpoints, handlers, env)
      } yield server
    )
  }

  override def makeClient[M[+_] <: MinMetadata[_], Ids: Tag](
    endpoints: Endpoints[M, Ids]
  ): ZLayer[Has[ClientConfig] with Clock with Logging, IOException, Has[ClientService[Ids]]] =
    ZLayer.fromManaged(
      for {
        config <- ZIO.service[ClientConfig].toManaged_
        client <- HttpClient.build(config, endpoints)
      } yield client
    )

  override def makeDocs[R, M[+_] <: MinMetadata[_]](endpoints: Endpoints[M, _]): ProtocolDocs =
    ???
}
