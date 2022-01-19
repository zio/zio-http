package zhttp.internal

import sttp.client3
import sttp.client3.asynchttpclient.zio.{SttpClient, send}
import sttp.client3.{UriContext, asWebSocketUnsafe, basicRequest}
import sttp.model.{Header => SHeader}
import sttp.ws.WebSocket
import zhttp.http.URL.Location
import zhttp.http._
import zhttp.internal.DynamicServer.HttpEnv
import zhttp.internal.HttpRunnableSpec.HttpIO
import zhttp.service._
import zhttp.service.client.ClientSSLHandler.ClientSSLOptions
import zio.test.DefaultRunnableSpec
import zio.{Has, Task, ZIO, ZManaged}

/**
 * Should be used only when e2e tests needs to be written. Typically we would want to do that when we want to test the
 * logic that is part of the netty based backend. For most of the other use cases directly running the HttpApp should
 * suffice. HttpRunnableSpec spins of an actual Http server and makes requests.
 */
abstract class HttpRunnableSpec extends DefaultRunnableSpec { self =>

  implicit class RunnableClientHttpSyntax[R, A](app: Http[R, Throwable, Client.ClientRequest, A]) {
    def run(
      path: Path = !!,
      method: Method = Method.GET,
      content: String = "",
      headers: Headers = Headers.empty,
    ): ZIO[R, Throwable, A] =
      app(
        Client.ClientRequest(
          method,
          URL(path, Location.Absolute(Scheme.HTTP, "localhost", 0)),
          headers,
          HttpData.fromString(content),
        ),
      ).catchAll {
        case Some(value) => ZIO.fail(value)
        case None        => ZIO.fail(new RuntimeException("No response"))
      }
  }

  implicit class RunnableHttpClientAppSyntax(app: HttpApp[HttpEnv, Throwable]) {
    def deploy: HttpIO[Any, Client.ClientResponse] =
      for {
        port     <- Http.fromZIO(DynamicServer.getPort)
        id       <- Http.fromZIO(DynamicServer.deploy(app))
        response <- Http.fromFunctionZIO[Client.ClientRequest] { params =>
          Client.request(
            params
              .addHeader(DynamicServer.APP_ID, id)
              .copy(url = URL(params.url.path, Location.Absolute(Scheme.HTTP, "localhost", port))),
            ClientSSLOptions.DefaultSSL,
          )
        }
      } yield response

    def deployWebSocket: HttpIO[SttpClient, client3.Response[Either[String, WebSocket[Task]]]] = for {
      id  <- Http.fromZIO(DynamicServer.deploy(app))
      res <-
        Http.fromFunctionZIO[Client.ClientRequest](params =>
          for {
            port <- DynamicServer.getPort
            url        = s"ws://localhost:$port${params.url.path.asString}"
            headerConv = params.addHeader(DynamicServer.APP_ID, id).getHeaders.toList.map(h => SHeader(h._1, h._2))
            res <- send(basicRequest.get(uri"$url").copy(headers = headerConv).response(asWebSocketUnsafe))
          } yield res,
        )

    } yield res

  }

  def serve[R <: Has[_]](
    app: HttpApp[R, Throwable],
  ): ZManaged[R with EventLoopGroup with ServerChannelFactory with DynamicServer, Nothing, Unit] =
    for {
      start <- Server.make(Server.app(app) ++ Server.port(0) ++ Server.paranoidLeakDetection).orDie
      _     <- DynamicServer.setStart(start).toManaged_
    } yield ()

  def status(
    method: Method = Method.GET,
    path: Path,
  ): ZIO[EventLoopGroup with ChannelFactory with DynamicServer, Throwable, Status] = {
    for {
      port   <- DynamicServer.getPort
      status <- Client
        .request(
          method,
          URL(path, Location.Absolute(Scheme.HTTP, "localhost", port)),
          ClientSSLOptions.DefaultSSL,
        )
        .map(_.status)
    } yield status
  }
}

object HttpRunnableSpec {
  type HttpIO[-R, +A] =
    Http[
      R with EventLoopGroup with ChannelFactory with DynamicServer with ServerChannelFactory,
      Throwable,
      Client.ClientRequest,
      A,
    ]
}
