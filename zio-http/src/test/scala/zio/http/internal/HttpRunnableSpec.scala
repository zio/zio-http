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

  implicit class RunnableClientHttpSyntax[R, A](app: Http[R, Throwable, Request, A]) {

    /**
     * Runs the deployed Http app by making a real http request to it. The
     * method allows us to configure individual constituents of a ClientRequest.
     */
    def run(
      path: Path = Root,
      method: Method = Method.GET,
      body: Body = Body.empty,
      headers: Headers = Headers.empty,
      version: Version = Version.Http_1_1,
      addZioUserAgentHeader: Boolean = false,
    ): ZIO[R, Throwable, A] =
      app
        .runZIO(
          Request(
            body,
            headers.combineIf(addZioUserAgentHeader)(Headers(Client.defaultUAHeader)),
            method,
            url = URL(path), // url set here is overridden later via `deploy` method
            version,
            None,
          ),
        )
        .catchAll {
          case Some(value) => ZIO.fail(value)
          case None        => ZIO.fail(new RuntimeException("No response"))
        }
  }

  implicit class RunnableHttpClientAppSyntax[R, E](route: HttpApp[R, E]) {

    def app: App[R] =
      route.withDefaultErrorResponse

    /**
     * Deploys the http application on the test server and returns a Http of
     * type {{{Http[R, E, ClientRequest, ClientResponse}}}. This allows us to
     * assert using all the powerful operators that are available on `Http`
     * while writing tests. It also allows us to simply pass a request in the
     * end, to execute, and resolve it with a response, like a normal HttpApp.
     */
    def deploy: Http[R with Client with DynamicServer with Scope, Throwable, Request, Response] =
      Http.fromHandler {
        for {
          port     <- Handler.fromZIO(DynamicServer.port)
          id       <- Handler.fromZIO(DynamicServer.deploy[R](app))
          client   <- Handler.fromZIO(ZIO.service[Client])
          response <- Handler.fromFunctionZIO[Request] { params =>
            client
              .request(
                params
                  .addHeader(DynamicServer.APP_ID, id)
                  .copy(url = URL(params.url.path, Location.Absolute(Scheme.HTTP, "localhost", port))),
              )
              .flatMap(_.collect)
          }
        } yield response
      }

    def deployAndRequest(
      call: Client => ZIO[Scope, Throwable, Response],
    ): Handler[Client with DynamicServer with R with Scope, Throwable, Any, Response] = {
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
    }

    def deployChunked: Http[R with Client with DynamicServer with Scope, Throwable, Request, Response] =
      Http.fromHandler {
        for {
          port     <- Handler.fromZIO(DynamicServer.port)
          id       <- Handler.fromZIO(DynamicServer.deploy(app))
          response <- Handler.fromFunctionZIO[Request] { params =>
            Client.request(
              params
                .addHeader(DynamicServer.APP_ID, id)
                .copy(url = URL(params.url.path, Location.Absolute(Scheme.HTTP, "localhost", port))),
            )
          }
        } yield response
      }

    def deployWS: Http[R with Client with DynamicServer with Scope, Throwable, SocketApp[Client with Scope], Response] =
      Http.fromHandler {
        for {
          id       <- Handler.fromZIO(DynamicServer.deploy[R](app))
          rawUrl   <- Handler.fromZIO(DynamicServer.wsURL)
          url      <- Handler.fromEither(URL.decode(rawUrl)).orDie
          response <- Handler.fromFunctionZIO[SocketApp[Client with Scope]] { app =>
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
  }

  def serve: ZIO[DynamicServer with Server, Nothing, Int] =
    for {
      server <- ZIO.service[Server]
      ds     <- ZIO.service[DynamicServer]
      app = DynamicServer.app(ds)
      port <- Server.install(app)
      _    <- DynamicServer.setStart(server)
    } yield port

  def serve[R](app: App[R]): ZIO[R with DynamicServer with Server, Nothing, Int] =
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
