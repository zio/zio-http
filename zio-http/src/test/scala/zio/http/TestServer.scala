package zio.http

import io.netty.handler.ssl.SslContextBuilder
import zio._
import zio.http.Server.ErrorCallback
import zio.http.URL.Location
import zio.http.middleware.HttpMiddleware
import zio.http.model.{Headers, Method, Scheme, Version}
import zio.http.netty.EventLoopGroups
import zio.http.netty.client.ClientSSLHandler
import zio.http.netty.client.ClientSSLHandler.ClientSSLOptions
import zio.http.service.ClientHttpsSpec.trustManagerFactory
import zio.http.socket.SocketApp

trait TestClient extends http.Client {
  def feedResponses(responses: Response*): UIO[Unit]
  def interactions(): UIO[List[(Request, Response)]]
//  def request(
//               request: Request,
//             )(implicit trace: Trace): ZIO[Client, Throwable, Response]
}
object TestClient {
  def interactions(): ZIO[TestClient, Nothing, List[(Request, Response)]] = ZIO.serviceWithZIO[TestClient](_.interactions())
  class Test(
              live: http.Client,
              responsesR: Ref[List[(Request, Response)]]
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

    def interactions(): UIO[List[(Request, Response)]] =
      responsesR.get

    def feedResponses(responses: Response*): UIO[Unit] =
      ???

    //      this.responsesR.update(_ ++ responses)
    def send(request: Request): ZIO[Clock, Throwable, Response] = {
      for {
        response <- live.request(request)
        _ <- responsesR.update(x => x :+ (request, response))

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
          _ <- ZIO.debug("Eh?")
          response <- live.requestInternal(body, headers, hostOption, method, pathPrefix, portOption, queries, sslOption, version).either
          _ <- ZIO.debug("Any?")
          rez <- response match {
            case Left(value) =>
              responsesR.update(interactions => (Request(version=version, method=method, url=URL(pathPrefix, Location.Absolute(Scheme.HTTP, "localhost", port = portOption.getOrElse(-1))), headers=headers, body=body), Response()) :: interactions) *>
              ZIO.fail(value)
            case Right(value) =>
              responsesR.update(interactions => (Request(), value) :: interactions) *>
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
      responses <- ZLayer.fromZIO(Ref.make(List.empty[(Request, Response)]))
      live <- Scope.default >+> configLayer >>> Client.default.orDie
      //      live = Server.ServerLiveHardcoded
    } yield ZEnvironment(new Test(live.get, responses.get))
  }
}

// TODO Add Trace to all signatures when closer to completion
trait TestServer extends Server {
  def responses: ZIO[Any, Nothing, List[Response]]
  def requests: ZIO[Any, Nothing, List[Request]]
  def feedRequests(responses: Request*): ZIO[Any, Nothing, Unit]

  // TODO What all do we want here?
  def feedResponses(responses: Request*): ZIO[Any, Nothing, Unit] = ???
}


object TestServer {
  def responses = ZIO.serviceWithZIO[TestServer](_.responses)
  def requests = ZIO.serviceWithZIO[TestServer](_.requests)
  def feedRequests(requests: Request*) = ZIO.serviceWithZIO[TestServer](_.feedRequests(requests:_*))
  class Test(
              //            live: HttpLive,
              live: Server,
              requestsR: Ref[List[Request]],
              responsesR: Ref[List[Response]]
            ) extends TestServer {
    override def responses: ZIO[Any, Nothing, List[Response]] =
      responsesR.get

    override def requests: ZIO[Any, Nothing, List[Request]] =
      requestsR.get

    override def feedRequests(requests: Request*): ZIO[Any, Nothing, Unit] =
      requestsR.update(_ ++ requests)

    val trackingMiddleware: HttpMiddleware[Any, Nothing] = {
      new Middleware[Any, Nothing, Request, Response, Request, Response] {
        /**
         * Applies middleware on Http and returns new Http.
         */
        override def apply[R1 <: Any, E1 >: Nothing](http: Http[R1, E1, Request, Response]): Http[R1, E1, Request, Response] =
          Http.fromOptionFunction[Request] { req =>
            for {
              _ <- requestsR.update(_ :+ req)
              response <- http(req)
            } yield response
          }
      }
    }

    override def install[R](httpApp: HttpApp[R, Throwable], errorCallback: Option[ErrorCallback]): URIO[R, Unit] = {
      ZIO.debug("TestServer.install") *>
      live.install(httpApp @@ trackingMiddleware, errorCallback)
//      ZIO.succeed(unsafe.print(line)(Unsafe.unsafe)) *>
//      live.provide(Server.install(httpApp))
//        .whenZIO(debugState.get) // TOOD Restore at some point
//        .unit
    }

    override def port: Int = live.port
  }


  def make: ZLayer[Any, Nothing, TestServer] =
    for {
      requests <- ZLayer.fromZIO(Ref.make(List.empty[Request]))
      responses <- ZLayer.fromZIO(Ref.make(List.empty[Response]))
      live <- Server.default.orDie
      //      live = Server.ServerLiveHardcoded
    } yield ZEnvironment(new Test(live.get, requests.get, responses.get))
}
