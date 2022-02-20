package zhttp.internal

import zhttp.http.URL.Location
import zhttp.http._
import zhttp.internal.DynamicServer.HttpEnv
import zhttp.internal.HttpRunnableSpec.HttpTestClient
import zhttp.service.Client.{ClientRequest, ClientResponse}
import zhttp.service._
import zhttp.service.client.ClientSSLHandler.ClientSSLOptions
import zhttp.socket.SocketApp
import zio.test.DefaultRunnableSpec
import zio.{Has, ZIO, ZManaged}

/**
 * Should be used only when e2e tests needs to be written. Typically we would
 * want to do that when we want to test the logic that is part of the netty
 * based backend. For most of the other use cases directly running the HttpApp
 * should suffice. HttpRunnableSpec spins of an actual Http server and makes
 * requests.
 */
abstract class HttpRunnableSpec extends DefaultRunnableSpec { self =>

  implicit class RunnableClientHttpSyntax[R, A](app: Http[R, Throwable, Client.ClientRequest, A]) {

    /**
     * Runs the deployed Http app by making a real http request to it. The
     * method allows us to configure individual constituents of a ClientRequest.
     */
    def run(
      path: Path = !!,
      method: Method = Method.GET,
      content: String = "",
      headers: Headers = Headers.empty,
      version: Version = Version.Http_1_1,
    ): ZIO[R, Throwable, A] =
      app(
        Client.ClientRequest(
          url = URL(path), // url set here is overridden later via `deploy` method
          method = method,
          headers = headers,
          data = HttpData.fromString(content),
          version = version,
        ),
      ).catchAll {
        case Some(value) => ZIO.fail(value)
        case None        => ZIO.fail(new RuntimeException("No response"))
      }
  }

  implicit class RunnableHttpClientAppSyntax(app: HttpApp[HttpEnv, Throwable]) {

    /**
     * Deploys the http application on the test server and returns a Http of
     * type {{{Http[R, E, ClientRequest, ClientResponse}}}. This allows us to
     * assert using all the powerful operators that are available on `Http`
     * while writing tests. It also allows us to simply pass a request in the
     * end, to execute, and resolve it with a response, like a normal HttpApp.
     */
    def deploy: HttpTestClient[Any, ClientRequest, ClientResponse] =
      for {
        port     <- Http.fromZIO(DynamicServer.port)
        id       <- Http.fromZIO(DynamicServer.deploy(app))
        response <- Http.fromFunctionZIO[Client.ClientRequest] { params =>
          Client.request(
            params
              .addHeader(DynamicServer.APP_ID, id)
              .copy(url = URL(params.url.path, Location.Absolute(Scheme.HTTP, "localhost", port))),
          )
        }
      } yield response

    def deployWS: HttpTestClient[Any, SocketApp[Any], ClientResponse] =
      for {
        id       <- Http.fromZIO(DynamicServer.deploy(app))
        url      <- Http.fromZIO(DynamicServer.wsURL)
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
      port   <- DynamicServer.port
      status <- Client
        .request(
          "http://localhost:%d/%s".format(port, path),
          method,
          ssl = ClientSSLOptions.DefaultSSL,
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
