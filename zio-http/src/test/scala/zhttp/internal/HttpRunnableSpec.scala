package zhttp.internal

import zhttp.http.URL.Location
import zhttp.http._
import zhttp.service.Client.Config
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

  implicit class RunnableClientHttpSyntax[R, A](app: Http[R, Throwable, Request, A]) {

    /**
     * Runs the deployed Http app by making a real http request to it. The
     * method allows us to configure individual constituents of a ClientRequest.
     */
    def run(
      path: Path = !!,
      method: Method = Method.GET,
      content: HttpData = HttpData.empty,
      headers: Headers = Headers.empty,
      version: Version = Version.Http_1_1,
    ): ZIO[R, Throwable, A] =
      app(
        Request(
          url = URL(path), // url set here is overridden later via `deploy` method
          method = method,
          headers = headers,
          data = content,
          version = version,
        ),
      ).catchAll {
        case Some(value) => ZIO.fail(value)
        case None        => ZIO.fail(new RuntimeException("No response"))
      }
  }

  implicit class RunnableHttpClientAppSyntax[R, E](http: HttpApp[R, E]) {

    def app(implicit e: E <:< Throwable): HttpApp[R, Throwable] =
      http.asInstanceOf[HttpApp[R, Throwable]]

    /**
     * Deploys the http application on the test server and returns a Http of
     * type {{{Http[R, E, ClientRequest, ClientResponse}}}. This allows us to
     * assert using all the powerful operators that are available on `Http`
     * while writing tests. It also allows us to simply pass a request in the
     * end, to execute, and resolve it with a response, like a normal HttpApp.
     */
    def deploy(implicit e: E <:< Throwable): Http[R with HttpEnv, Throwable, Request, Response] =
      for {
        port     <- Http.fromZIO(DynamicServer.port)
        id       <- Http.fromZIO(DynamicServer.deploy(app))
        response <- Http.fromFunctionZIO[Request] { params =>
          Client.request(
            params
              .addHeader(DynamicServer.APP_ID, id)
              .copy(url = URL(params.url.path, Location.Absolute(Scheme.HTTP, "localhost", port))),
            Config.empty,
          )
        }
      } yield response

    def deployWS(implicit e: E <:< Throwable): Http[R with HttpEnv, Throwable, SocketApp[HttpEnv], Response] =
      for {
        id       <- Http.fromZIO(DynamicServer.deploy(app))
        url      <- Http.fromZIO(DynamicServer.wsURL)
        response <- Http.fromFunctionZIO[SocketApp[HttpEnv]] { app =>
          Client
            .socket(
              url = url,
              headers = Headers(DynamicServer.APP_ID, id),
              app = app,
            )
            .useNow
        }
      } yield response
  }

  def serve[R <: Has[_]](
    app: HttpApp[R, Throwable],
    server: Option[Server[R, Throwable]] = None,
  ): ZManaged[R with EventLoopGroup with ServerChannelFactory with DynamicServer, Nothing, Unit] =
    for {
      settings <- ZManaged
        .succeed(
          server.foldLeft(
            Server.app(app) ++ Server.port(0) ++ Server.paranoidLeakDetection,
          )(
            _ ++ _,
          ),
        )
      start    <- Server.make(settings).orDie
      _        <- DynamicServer.setStart(start).toManaged_
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
