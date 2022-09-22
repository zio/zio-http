package zio.http.internal

import zio.http.URL.Location
import zio.http._
import zio.http.model._
import zio.http.socket.SocketApp
import zio.test.ZIOSpecDefault
import zio.{Scope, ZIO}

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
      addZioUserAgentHeader: Boolean = false,
    ): ZIO[R, Throwable, A] =
      app(
        Request(
          body,
          headers.combineIf(addZioUserAgentHeader)(Client.defaultUAHeader),
          method,
          url = URL(path), // url set here is overridden later via `deploy` method
          version,
          None,
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
    def deploy(implicit
      e: E <:< Throwable,
    ): Http[R with Client with DynamicServer with Scope, Throwable, Request, Response] =
      for {
        port     <- Http.fromZIO(DynamicServer.port)
        id       <- Http.fromZIO(DynamicServer.deploy(app))
        response <- Http.fromFunctionZIO[Request] { params =>
          Client.request(
            params
              .addHeader(DynamicServer.APP_ID, id)
              .copy(url = URL(params.url.path, Location.Absolute(Scheme.HTTP, "localhost", port))),
          )
        }
      } yield response

    def deployChunked(implicit
      e: E <:< Throwable,
    ): Http[R with Client with DynamicServer, Throwable, Request, Response] =
      for {
        port     <- Http.fromZIO(DynamicServer.port)
        id       <- Http.fromZIO(DynamicServer.deploy(app))
        response <- Http.fromFunctionZIO[Request] { params =>
          Client.request(
            params
              .addHeader(DynamicServer.APP_ID, id)
              .copy(url = URL(params.url.path, Location.Absolute(Scheme.HTTP, "localhost", port))),
          )
        }
      } yield response
    def deployWS(implicit
      e: E <:< Throwable,
    ): Http[R with Client with DynamicServer with Scope, Throwable, SocketApp[Client with Scope], Response] =
      for {
        id       <- Http.fromZIO(DynamicServer.deploy(app))
        url      <- Http.fromZIO(DynamicServer.wsURL)
        response <- Http.fromFunctionZIO[SocketApp[Client with Scope]] { app =>
          ZIO.scoped[Client with Scope](
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
    app: HttpApp[R, Throwable],
  ): ZIO[R with DynamicServer with Server, Nothing, Int] =
    for {
      server <- ZIO.service[Server]
      port   <- Server.install(app)
      _      <- DynamicServer.setStart(server)
    } yield port

  def status(
    method: Method = Method.GET,
    path: Path,
  ): ZIO[Client with DynamicServer with Scope, Throwable, Status] = {
    for {
      port   <- DynamicServer.port
      status <- Client
        .request(
          "http://localhost:%d/%s".format(port, path),
          method,
        )
        .map(_.status)
    } yield status
  }

  def headers(
    method: Method = Method.GET,
    path: Path,
    headers: Headers = Headers.empty,
  ): ZIO[Client with DynamicServer with Scope, Throwable, Headers] = {
    for {
      port    <- DynamicServer.port
      headers <- Client
        .request(
          "http://localhost:%d/%s".format(port, path),
          method,
          headers = headers,
        )
        .map(_.headers)
    } yield headers
  }
}
