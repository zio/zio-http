package zhttp.internal

import zhttp.http.URL.Location
import zhttp.http._
import zhttp.internal.DynamicServer.HttpEnv
import zhttp.internal.HttpRunnableSpec.HttpTestClient
import zhttp.service.Client.ClientResponse
import zhttp.service._
import zhttp.service.client.ClientSSLHandler.ClientSSLOptions
import zhttp.socket.SocketApp
import zio.test.DefaultRunnableSpec
import zio.{Has, ZIO, ZManaged}

/**
 * Should be used only when e2e tests needs to be written. Typically we would want to do that when we want to test the
 * logic that is part of the netty based backend. For most of the other use cases directly running the HttpApp should
 * suffice. HttpRunnableSpec spins of an actual Http server and makes requests.
 */
abstract class HttpRunnableSpec extends DefaultRunnableSpec { self =>

  implicit class RunnableClientHttpSyntax[R, A](app: Http[R, Throwable, Request, A]) {

    /**
     * Runs the deployed Http app by making a real http request to it. The method allows us to configure individual
     * constituents of a Request.
     */
    def run(
      path: Path = !!,
      method: Method = Method.GET,
      content: String = "",
      headers: Headers = Headers.empty,
    ): ZIO[R, Throwable, A] =
      app(
        Request(
          method,
          URL(path, Location.Absolute(Scheme.HTTP, "localhost", 0)),
          headers,
          HttpData.fromString(content),
          None,
        ),
      ).catchAll {
        case Some(value) => ZIO.fail(value)
        case None        => ZIO.fail(new RuntimeException("No response"))
      }
  }

  implicit class RunnableHttpClientAppSyntax(app: HttpApp[HttpEnv, Throwable]) {

    /**
     * Deploys the http application on the test server and returns a Http of type
     * {{{Http[R, E, Request, ClientResponse}}}. This allows us to assert using all the powerful operators that are
     * available on `Http` while writing tests. It also allows us to simply pass a request in the end, to execute, and
     * resolve it with a response, like a normal HttpApp.
     */
    def deploy: HttpTestClient[Any, Request, ClientResponse] =
      for {
        port     <- Http.fromZIO(DynamicServer.getPort)
        id       <- Http.fromZIO(DynamicServer.deploy(app))
        response <- Http.fromFunctionZIO[Request] { params =>
          Client.request(
            params
              .addHeader(DynamicServer.APP_ID, id)
              .copy(url = URL(params.url.path, Location.Absolute(Scheme.HTTP, "localhost", port))),
            ClientSSLOptions.DefaultSSL,
          )
        }
      } yield response

    def deployWS: HttpTestClient[Any, SocketApp[Any], ClientResponse] =
      for {
        id       <- Http.fromZIO(DynamicServer.deploy(app))
        url      <- Http.fromZIO(DynamicServer.baseURL)
        response <- Http.fromFunctionZIO[SocketApp[Any]] { app =>
          Client.socket(
            url = url,
            headers = Headers(DynamicServer.APP_ID, id),
            app = app,
          )
        }
      } yield response
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
  type HttpTestClient[-R, -A, +B] =
    Http[
      R with EventLoopGroup with ChannelFactory with DynamicServer with ServerChannelFactory,
      Throwable,
      A,
      B,
    ]
}
