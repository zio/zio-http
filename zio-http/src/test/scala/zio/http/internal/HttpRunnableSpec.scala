package zio.http.internal

import zio.ZIO
import zio.http.Client.Config
import zio.http.URL.Location
import zio.http._
import zio.http.service.ClientSSLHandler.ClientSSLOptions
import zio.http.service._
import zio.http.socket.SocketApp
import zio.test.ZIOSpecDefault

/**
 * Should be used only when e2e tests needs to be written. Typically we would
 * want to do that when we want to test the logic that is part of the netty
 * based backend. For most of the other use cases directly running the HttpApp
 * should suffice. HttpRunnableSpec spins of an actual Http server and makes
 * requests.
 */
abstract class HttpRunnableSpec extends ZIOSpecDefault { self =>

  implicit class RunnableClientHttpSyntax[R, A](app: Http[R, Throwable, Request, A]) {

    /**
     * Runs the deployed Http app by making a real http request to it. The
     * method allows us to configure individual constituents of a ClientRequest.
     */
    def run(
      path: Path = !!,
      method: Method = Method.GET,
      body: Body = Body.empty,
      headers: Headers = Headers.empty,
      version: Version = Version.Http_1_1,
    ): ZIO[R, Throwable, A] =
      app(
        Request(
          url = URL(path), // url set here is overridden later via `deploy` method
          method = method,
          headers = headers,
          body = body,
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
    def deploy(implicit e: E <:< Throwable): Http[R with HttpEnv with EventLoopGroup, Throwable, Request, Response] =
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

    def deployWS(implicit
      e: E <:< Throwable,
    ): Http[R with HttpEnv with EventLoopGroup, Throwable, SocketApp[HttpEnv], Response] =
      for {
        id       <- Http.fromZIO(DynamicServer.deploy(app))
        url      <- Http.fromZIO(DynamicServer.wsURL)
        response <- Http.fromFunctionZIO[SocketApp[HttpEnv with EventLoopGroup]] { app =>
          ZIO.scoped[HttpEnv with EventLoopGroup](
            Client
              .socket(
                url = url,
                headers = Headers(DynamicServer.APP_ID, id),
                app = app,
              ),
          )
        }
      } yield response
  }

  def serve[R](
    app: HttpApp[R, Throwable]
  ): ZIO[R with DynamicServer with Server, Nothing, Int] =
    for {
      server <- ZIO.service[Server]
      port     <- Server.install(app)
      _      <- DynamicServer.setStart(server)
    } yield port

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

  def headers(
    method: Method = Method.GET,
    path: Path,
    headers: Headers = Headers.empty,
  ): ZIO[EventLoopGroup with ChannelFactory with DynamicServer, Throwable, Headers] = {
    for {
      port    <- DynamicServer.port
      headers <- Client
        .request(
          "http://localhost:%d/%s".format(port, path),
          method,
          ssl = ClientSSLOptions.DefaultSSL,
          headers = headers,
        )
        .map(_.headers)
    } yield headers
  }
}
