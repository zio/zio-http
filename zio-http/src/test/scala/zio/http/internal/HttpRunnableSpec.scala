/*
 * Copyright 2021 - 2023 Sporta Technologies PVT LTD & the ZIO HTTP contributors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package zio.http.internal

import zio.test.ZIOSpecDefault
import zio.{Scope, ZIO}

import zio.http.URL.Location
import zio.http._

/**
 * Should be used only when e2e tests needs to be written. Typically we would
 * want to do that when we want to test the logic that is part of the netty
 * based backend. For most of the other use cases directly running the HttpApp
 * should suffice. HttpRunnableSpec spins of an actual Http server and makes
 * requests.
 */
abstract class HttpRunnableSpec extends ZIOSpecDefault { self =>

  implicit class RunnableClientHttpSyntax[R, A](app: Handler[R, Response, Request, A]) {

    /**
     * Runs the deployed Http app by making a real http request to it. The
     * method allows us to configure individual constituents of a ClientRequest.
     */
    def run(
      version: Version = Version.Default,
      method: Method = Method.ANY,
      path: Path = Root,
      headers: Headers = Headers.empty,
      body: Body = Body.empty,
      addZioUserAgentHeader: Boolean = false,
    ): ZIO[R, Response, A] =
      app
        .runZIO(
          Request(
            body = body,
            headers = headers.combineIf(addZioUserAgentHeader)(Headers(Client.defaultUAHeader)),
            method = method,
            url = URL(path), // url set here is overridden later via `deploy` method
            version = version,
            remoteAddress = None,
          ),
        )
  }

  implicit class RunnableHttpClientAppSyntax[R](route: HttpApp[R]) {

    def app: HttpApp[R] = route

    /**
     * Deploys the http application on the test server and returns a Http of
     * type {{{Http[R, E, ClientRequest, ClientResponse}}}. This allows us to
     * assert using all the powerful operators that are available on `Http`
     * while writing tests. It also allows us to simply pass a request in the
     * end, to execute, and resolve it with a response, like a normal HttpApp.
     */
    def deploy: Handler[DynamicServer with R with Client with Scope, Throwable, Request, Response] =
      for {
        port     <- Handler.fromZIO(DynamicServer.port)
        id       <- Handler.fromZIO(DynamicServer.deploy[R](app))
        client   <- Handler.fromZIO(ZIO.service[Client])
        response <- Handler.fromFunctionZIO[Request] { params =>
          client(
            params
              .addHeader(DynamicServer.APP_ID, id)
              .copy(url = URL(params.url.path, Location.Absolute(Scheme.HTTP, "localhost", port))),
          )
            .flatMap(_.collect)
        }
      } yield response

    def deployAndRequest(
      call: Client => ZIO[Scope, Throwable, Response],
    ): Handler[Client with DynamicServer with R with Scope, Throwable, Any, Response] =
      for {
        port     <- Handler.fromZIO(DynamicServer.port)
        id       <- Handler.fromZIO(DynamicServer.deploy[R](app))
        client   <- Handler.fromZIO(ZIO.service[Client])
        response <- Handler.fromZIO(
          call(
            client
              .addHeader(DynamicServer.APP_ID, id)
              .url(URL.decode(s"http://localhost:$port").toOption.get),
          ),
        )
      } yield response

    def deployChunked =
      for {
        port     <- Handler.fromZIO(DynamicServer.port)
        id       <- Handler.fromZIO(DynamicServer.deploy(app))
        client   <- Handler.fromZIO(ZIO.service[Client])
        response <- Handler.fromFunctionZIO[Request] { params =>
          client(
            params
              .addHeader(DynamicServer.APP_ID, id)
              .copy(url = URL(params.url.path, Location.Absolute(Scheme.HTTP, "localhost", port))),
          )
        }
      } yield response

    def deployWS
      : Handler[R with Client with DynamicServer with Scope, Throwable, SocketApp[Client with Scope], Response] =
      for {
        id       <- Handler.fromZIO(DynamicServer.deploy[R](app))
        rawUrl   <- Handler.fromZIO(DynamicServer.wsURL)
        url      <- Handler.fromEither(URL.decode(rawUrl)).orDie
        client   <- Handler.fromZIO(ZIO.service[Client])
        response <- Handler.fromFunctionZIO[SocketApp[Client with Scope]] { app =>
          ZIO.scoped[Client with Scope](
            client
              .url(url)
              .addHeaders(Headers(DynamicServer.APP_ID, id))
              .socket(app),
          )
        }
      } yield response
  }

  def serve: ZIO[DynamicServer with Server, Nothing, Int] =
    for {
      server <- ZIO.service[Server]
      ds     <- ZIO.service[DynamicServer]
      app = DynamicServer.app(ds)
      port <- Server.install(app.toHttpApp)
      _    <- DynamicServer.setStart(server)
    } yield port

  def serve[R](app: HttpApp[R]): ZIO[R with DynamicServer with Server, Nothing, Int] =
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
      client <- ZIO.service[Client]
      url = URL.decode("http://localhost:%d/%s".format(port, path)).toOption.get
      status <- client(Request(method = method, url = url)).map(_.status)
    } yield status
  }

  def headers(
    method: Method = Method.GET,
    path: Path,
    headers: Headers = Headers.empty,
  ): ZIO[Client with DynamicServer with Scope, Throwable, Headers] = {
    for {
      port <- DynamicServer.port
      url = URL.decode("http://localhost:%d/%s".format(port, path)).toOption.get
      headers <- ZClient
        .request(Request(method = method, headers = headers, url = url))
        .map(_.headers)
    } yield headers
  }
}
