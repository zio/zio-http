package zio.http

import zio._

/**
 * Enables tests that use a client without needing a live Server
 *
 * @param behavior
 *   Contains the user-specified behavior that takes the place of the usual
 *   Server
 */
final case class TestClient(
  behavior: Ref[Routes[Any, Response]],
  serverSocketBehavior: Ref[WebSocketApp[Any]],
) extends ZClient.Driver[Any, Scope, Throwable] {

  /**
   * Adds Routes to the TestClient
   */
  def addRoutes(
    routes: Routes[Any, Response],
  ): ZIO[Any, Nothing, Unit] =
    behavior.update(_ ++ routes)

  /**
   * Adds an exact 1-1 behavior
   * @param expectedRequest
   *   The request that will trigger the response
   * @param response
   *   The response to be returned when a user submits the response
   *
   * @example
   *   {{{
   *    TestClient.addRequestResponse(Request.get(URL.root), Response.ok)
   *   }}}
   */
  def addRequestResponse(
    expectedRequest: Request,
    response: Response,
  ): ZIO[Any, Nothing, Unit] = {

    def isDefinedAt(realRequest: Request): Boolean = {
      // The way that the Client breaks apart and re-assembles the request prevents a straightforward
      //    expectedRequest == realRequest
      expectedRequest.url.relative == realRequest.url &&
      expectedRequest.method == realRequest.method &&
      expectedRequest.headers.toSet.forall(expectedHeader => realRequest.headers.toSet.contains(expectedHeader))
    }
    addRoute(RoutePattern(expectedRequest.method, expectedRequest.path) -> handler { (realRequest: Request) =>
      if (!isDefinedAt(realRequest))
        throw new MatchError(s"TestClient received unexpected request: $realRequest (expected: $expectedRequest)")
      else response
    })
  }

  /**
   * Adds a route definition to handle requests that are submitted by test cases
   * @param route
   *   New route to be added to the TestClient
   * @tparam R
   *   Environment of the new route
   *
   * @example
   *   {{{
   *    TestClient.addRoute { Method.ANY / trailing -> handler(Response.ok) }
   *   }}}
   */
  def addRoute[R](
    route: Route[R, Response],
  ): ZIO[R, Nothing, Unit] =
    for {
      r <- ZIO.environment[R]
      provided = route.provideEnvironment(r)
      _ <- behavior.update(_ :+ provided)
    } yield ()

  /**
   * Adds routes to handle requests that are submitted by test cases
   * @param routes
   *   New routes to be added to the TestClient
   * @tparam R
   *   Environment of the new route
   *
   * @example
   *   {{{
   *   TestClient.addRoutes {
   *     Routes(
   *       Method.GET / trailing          -> handler { Response.text("fallback") },
   *       Method.GET / "hello" / "world" -> handler { Response.text("Hey there!") },
   *     )
   *   }
   *   }}}
   */
  def addRoutes[R](
    route: Route[R, Response],
    routes: Route[R, Response]*,
  ): ZIO[R, Nothing, Unit] =
    for {
      r <- ZIO.environment[R]
      provided = Routes.fromIterable(route +: routes).provideEnvironment(r)
      _ <- behavior.update(_ ++ provided)
    } yield ()

  def headers: Headers = Headers.empty

  def method: Method = Method.GET

  def sslConfig: Option[ClientSSLConfig] = None

  def url: URL = URL(Path.root)

  def version: Version = Version.Http_1_1

  override def request(
    version: Version,
    method: Method,
    url: URL,
    headers: Headers,
    body: Body,
    sslConfig: Option[zio.http.ClientSSLConfig],
    proxy: Option[Proxy],
  )(implicit trace: Trace): ZIO[Scope, Throwable, Response] = {
    for {
      currentBehavior <- behavior.get
      request = Request(
        body = body,
        headers = headers,
        method = if (method == Method.ANY) Method.GET else method,
        url = url.relative,
        version = version,
        remoteAddress = None,
      )
      response <- currentBehavior(request).merge
    } yield response
  }

  def socket[Env1](
    version: Version,
    url: URL,
    headers: Headers,
    app: WebSocketApp[Env1],
  )(implicit trace: Trace, ev: Scope =:= Scope): ZIO[Env1 & Scope, Throwable, Response] = {
    for {
      env                   <- ZIO.environment[Env1]
      currentSocketBehavior <- serverSocketBehavior.get
      in                    <- Queue.unbounded[WebSocketChannelEvent]
      out                   <- Queue.unbounded[WebSocketChannelEvent]
      promise               <- Promise.make[Nothing, Unit]
      testChannelClient     <- TestChannel.make(in, out, promise)
      testChannelServer     <- TestChannel.make(out, in, promise)
      _                     <- currentSocketBehavior.handler.runZIO(testChannelClient).forkDaemon
      _                     <- app.provideEnvironment(env).handler.runZIO(testChannelServer).forkDaemon
    } yield Response.status(Status.SwitchingProtocols)
  }

  def installSocketApp[Env1](
    app: WebSocketApp[Any],
  ): ZIO[Env1, Nothing, Unit] =
    for {
      env <- ZIO.environment[Env1]
      _   <- serverSocketBehavior.set(
        app
          .provideEnvironment(env),
      )
    } yield ()
}

object TestClient {

  def addRoutes(routes: Routes[Any, Response]): ZIO[TestClient, Nothing, Unit] =
    ZIO.serviceWithZIO[TestClient](_.addRoutes(routes))

  /**
   * Adds an exact 1-1 behavior
   * @param request
   *   The request that will trigger the response
   * @param response
   *   The response to be returned when a user submits the response
   *
   * @example
   *   {{{
   *    TestClient.addRequestResponse(Request.get(URL.root), Response.ok)
   *   }}}
   */
  def addRequestResponse(
    request: Request,
    response: Response,
  ): ZIO[TestClient, Nothing, Unit] =
    ZIO.serviceWithZIO[TestClient](_.addRequestResponse(request, response))

  /**
   * Adds a route definition to handle requests that are submitted by test cases
   * @param route
   *   New route to be added to the TestClient
   * @tparam R
   *   Environment of the new route
   *
   * @example
   *   {{{
   *    TestClient.addRoute { Method.ANY / trailing -> handler(Response.ok) }
   *   }}}
   */
  def addRoute[R](
    route: Route[R, Response],
  ): ZIO[R with TestClient, Nothing, Unit] =
    ZIO.serviceWithZIO[TestClient](_.addRoute(route))

  /**
   * Adds routes to handle requests that are submitted by test cases
   * @param routes
   *   New routes to be added to the TestClient
   * @tparam R
   *   Environment of the new route
   *
   * @example
   *   {{{
   *   TestClient.addRoutes {
   *     Routes(
   *       Method.GET / trailing          -> handler { Response.text("fallback") },
   *       Method.GET / "hello" / "world" -> handler { Response.text("Hey there!") },
   *     )
   *   }
   *   }}}
   */
  def addRoutes[R](
    route: Route[R, Response],
    routes: Route[R, Response]*,
  ): ZIO[R with TestClient, Nothing, Unit] =
    ZIO.serviceWithZIO[TestClient](_.addRoutes(route, routes: _*))

  def installSocketApp(
    app: WebSocketApp[Any],
  ): ZIO[TestClient, Nothing, Unit] =
    ZIO.serviceWithZIO[TestClient](_.installSocketApp(app))

  val layer: ZLayer[Any, Nothing, TestClient & Client] =
    ZLayer.scopedEnvironment {
      for {
        behavior       <- Ref.make[Routes[Any, Response]](Routes.empty)
        socketBehavior <- Ref.make[WebSocketApp[Any]](WebSocketApp.unit)
        driver = TestClient(behavior, socketBehavior)
      } yield ZEnvironment[TestClient, Client](driver, ZClient.fromDriver(driver))
    }

}
