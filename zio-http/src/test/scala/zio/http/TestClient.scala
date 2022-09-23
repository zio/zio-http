package zio.http

import zio.{UIO, http}
import io.netty.handler.ssl.SslContextBuilder
import zio._
import zio.http.URL.Location
import zio.http.model.{Headers, Method, Scheme, Version}
import zio.http.netty.EventLoopGroups
import zio.http.netty.client.ClientSSLHandler
import zio.http.netty.client.ClientSSLHandler.ClientSSLOptions
import zio.http.service.ClientHttpsSpec.trustManagerFactory
import zio.http.socket.SocketApp
import TestClient.Interaction

trait TestClient extends http.Client {
  def feedResponses(responses: Response*): UIO[Unit]

  def interactions(): UIO[List[Interaction]]
  //  def request(
  //               request: Request,
  //             )(implicit trace: Trace): ZIO[Client, Throwable, Response]
}

object TestClient {
  // TODO Should Response be one option, with error/exception being another?
  case class Interaction(request: Request, response: Response)
  def interactions(): ZIO[TestClient, Nothing, List[Interaction]] = ZIO.serviceWithZIO[TestClient](_.interactions())

  class Test(
              live: http.Client,
              responsesR: Ref[List[Interaction]]
            ) extends TestClient {

    //    def request(
    //                 request: Request,
    //               )(implicit trace: Trace): ZIO[Client, Throwable, Response] =
    //
    //      for {
    //        response <- live.request(request)
    //        _ <- responsesR.update(x => x :+ (request, response))
    //
    //      } yield response

    def interactions(): UIO[List[Interaction]] =
      responsesR.get

    def feedResponses(responses: Response*): UIO[Unit] =
      ???

    //      this.responsesR.update(_ ++ responses)
    def send(request: Request): ZIO[Clock, Throwable, Response] = {
      for {
        response <- live.request(request)
        _ <- responsesR.update(x => x :+ Interaction(request, response))

      } yield response
      //      responses.flatMap(_.take)
    }

    override def headers: Headers = live.headers

    override def hostOption: Option[String] = live.hostOption

    override def pathPrefix: Path = live.pathPrefix

    override def portOption: Option[Int] = live.portOption

    override def queries: QueryParams = live.queries

    override def schemeOption: Option[Scheme] = live.schemeOption

    override def sslOption: Option[ClientSSLHandler.ClientSSLOptions] = live.sslOption

    override def requestInternal(body: Body, headers: Headers, hostOption: Option[String], method: Method, pathPrefix: Path, portOption: Option[Int], queries: QueryParams, sslOption: Option[ClientSSLHandler.ClientSSLOptions], version: Version)(implicit trace: Trace): ZIO[Any, Throwable, Response] = {

      for {
        response <- live.requestInternal(body, headers, hostOption, method, pathPrefix, portOption, queries, sslOption, version).either
        rez <- response match {
          case Left(value) =>
            responsesR.update(interactions => Interaction(Request(version = version, method = method, url = URL(pathPrefix, Location.Absolute(Scheme.HTTP, "localhost", port = portOption.getOrElse(-1))), headers = headers, body = body), Response()) :: interactions) *>
              ZIO.fail(value)
          case Right(value) =>
            responsesR.update(interactions => Interaction(Request(version = version, method = method, url = URL(pathPrefix, Location.Absolute(Scheme.HTTP, "localhost", port = portOption.getOrElse(-1))), headers = headers, body = body), value) :: interactions) *>
              ZIO.succeed(value)
        }
        //          _ <- responsesR.update(interactions => (Request(), response) :: interactions)
      } yield rez
    }

    override def socketInternal[Env1 <: Any](app: SocketApp[Env1], headers: Headers, hostOption: Option[String], pathPrefix: Path, portOption: Option[Int], queries: QueryParams, schemeOption: Option[Scheme], version: Version)(implicit trace: Trace): ZIO[Env1 with Scope, Throwable, Response] =
      live.socketInternal(app, headers, hostOption, pathPrefix, portOption, queries, schemeOption, version)

  }

  def make: ULayer[TestClient] = {

    val sslOption: ClientSSLOptions = // TODO Need this?
      ClientSSLOptions.CustomSSL(SslContextBuilder.forClient().trustManager(trustManagerFactory).build())
    val configLayer: ZLayer[Any, Nothing, EventLoopGroups.Config] = ZLayer.succeed(ClientConfig.empty.ssl(sslOption))
    for {
      responses <- ZLayer.fromZIO(Ref.make(List.empty[Interaction]))
      live <- Scope.default >+> configLayer >>> Client.default.orDie
      //      live = Server.ServerLiveHardcoded
    } yield ZEnvironment(new Test(live.get, responses.get))
  }
}