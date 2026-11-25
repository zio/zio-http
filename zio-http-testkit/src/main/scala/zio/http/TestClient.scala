package zio.http

import zio._

/**
 * Enables tests that use a client without needing a live Server
 *
 * @param behavior
 *   Contains the user-specified behavior that takes the place of the usual
 *   Server
 * @param serverSocketBehavior
 *   The WebSocket app that serves the connections opened through `socket` and
 *   `socketChannel`
 * @param missingRouteHandler
 *   The handler that answers requests that no route in `behavior` matches
 */
final case class TestClient(
  behavior: Ref[Routes[Any, Response]],
  serverSocketBehavior: Ref[WebSocketApp[Any]],
  missingRouteHandler: Ref[Handler[Any, Response, Request, Response]],
) extends ZClient.Driver[Any, Scope, Throwable] {

  /**
   * Adds Routes to the TestClient
   */
  def addRoutes(
    routes: Routes[Any, Response],
  ): ZIO[Any, Nothing, Unit] =
    behavior.update(_ ++ routes)

  /**
   * Adds an exact 1-1 behavior. A request with the same method and path as
   * `expectedRequest` gets `response` when its URL matches and its headers
   * include all headers of `expectedRequest`. When the method and path match
   * but the URL or headers differ, the handler throws a `scala.MatchError` and
   * the client receives a response with status 500.
   *
   * @param expectedRequest
   *   The request that will trigger the response
   * @param response
   *   The response to be returned when a user submits the response
   *
   * @example
   *   {{{
   *   import zio._
   *
   *   def register(client: TestClient): UIO[Unit] =
   *     client.addRequestResponse(Request.get(URL.root), Response.ok)
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
   *   import zio._
   *   import zio.http.codec.PathCodec.trailing
   *
   *   def register(client: TestClient): UIO[Unit] =
   *     client.addRoute(Method.ANY / trailing -> handler(Response.ok))
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
   *   import zio._
   *   import zio.http.codec.PathCodec.trailing
   *
   *   def register(client: TestClient): UIO[Unit] =
   *     client.addRoutes(
   *       Method.GET / trailing          -> handler { Response.text("fallback") },
   *       Method.GET / "hello" / "world" -> handler { Response.text("Hey there!") },
   *     )
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

  /**
   * Sets a fallback behaviour for when the TestClient attempts to access any
   * unspecified routes
   * @param fallbackFunction
   *   The function to execute when the client attempts to perform a request not
   *   part of its behavior
   * @tparam R
   *   Environment of the handler
   *
   * @example
   *   {{{
   *   import zio._
   *
   *   def record(client: TestClient, seen: Ref[List[Request]]): UIO[Unit] =
   *     client.setFallbackHandler(req => seen.update(req :: _).as(Response.notFound))
   *   }}}
   */
  def setFallbackHandler[R](fallbackFunction: Request => ZIO[R, Response, Response]): ZIO[R, Nothing, Unit] =
    for {
      r <- ZIO.environment[R]
      fallbackHandler = handler(fallbackFunction).provideEnvironment(r)
      _ <- missingRouteHandler.set(fallbackHandler)
    } yield ()

  /** Returns `Headers.empty`. */
  def headers: Headers = Headers.empty

  /** Returns `Method.GET`. */
  def method: Method = Method.GET

  /** Returns `None`. */
  def sslConfig: Option[ClientSSLConfig] = None

  /** Returns the URL with the root path `/`. */
  def url: URL = URL(Path.root)

  /** Returns `Version.Http_1_1`. */
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
      missingRouteBehavior <- missingRouteHandler.get.map(_.toRoutes)
      currentBehavior      <- behavior.get.map(missingRouteBehavior ++ _)
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

  /**
   * Opens a channel pair as `socketChannel` does, runs `app` on the client side
   * of the channel in a daemon fiber, and returns the handshake response.
   */
  def socket[Env1](
    version: Version,
    url: URL,
    headers: Headers,
    app: WebSocketApp[Env1],
  )(implicit trace: Trace, ev: Scope =:= Scope): ZIO[Env1 & Scope, Throwable, Response] = {
    for {
      env    <- ZIO.environment[Env1]
      result <- socketChannel(version, url, headers, app.customConfig)
      (response, channel) = result
      _ <- app.provideEnvironment(env).handler.runZIO(channel).forkDaemon
    } yield response
  }

  /**
   * Opens a pair of connected `TestChannel`s: the installed server socket app
   * runs on the server side in a daemon fiber, and the client side is returned
   * with a response with status 101 (`Status.SwitchingProtocols`).
   */
  override def socketChannel(
    version: Version,
    url: URL,
    headers: Headers,
    config: Option[WebSocketConfig],
  )(implicit trace: Trace, ev: Scope =:= Scope): ZIO[Scope, Throwable, (Response, WebSocketChannel)] =
    for {
      currentSocketBehavior <- serverSocketBehavior.get
      in                    <- Queue.unbounded[WebSocketChannelEvent]
      out                   <- Queue.unbounded[WebSocketChannelEvent]
      promise               <- Promise.make[Nothing, Unit]
      serverChannel         <- TestChannel.make(in, out, promise)
      clientChannel         <- TestChannel.make(out, in, promise)
      _                     <- currentSocketBehavior.handler.runZIO(serverChannel).forkDaemon
    } yield (Response.status(Status.SwitchingProtocols), clientChannel)

  /**
   * Sets the WebSocket app that serves the connections opened through `socket`
   * and `socketChannel`, replacing the previous one.
   */
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

/**
 * Accessors for [[TestClient]] and the layer that provides it together with a
 * `Client` that sends its requests to the `TestClient`.
 *
 * {{{
 * import zio._
 *
 * val program: ZIO[TestClient & Client, Throwable, String] =
 *   for {
 *     _        <- TestClient.addRoutes(Routes(Method.GET / "hello" -> handler(Response.text("Hi"))))
 *     response <- Client.batched(Request.get("/hello"))
 *     body     <- response.body.asString
 *   } yield body // "Hi"
 *
 * val result: Task[String] = program.provide(TestClient.layer)
 * }}}
 */
object TestClient {

  /** Adds the routes to the `TestClient` in the environment. */
  def addRoutes(routes: Routes[Any, Response]): ZIO[TestClient, Nothing, Unit] =
    ZIO.serviceWithZIO[TestClient](_.addRoutes(routes))

  /**
   * Adds an exact 1-1 behavior to the `TestClient` in the environment. See
   * `TestClient#addRequestResponse` for how requests are matched.
   *
   * @param request
   *   The request that will trigger the response
   * @param response
   *   The response to be returned when a user submits the response
   *
   * @example
   *   {{{
   *   import zio._
   *
   *   val register: URIO[TestClient, Unit] =
   *     TestClient.addRequestResponse(Request.get(URL.root), Response.ok)
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
   *   import zio._
   *   import zio.http.codec.PathCodec.trailing
   *
   *   val register: URIO[TestClient, Unit] =
   *     TestClient.addRoute(Method.ANY / trailing -> handler(Response.ok))
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
   *   import zio._
   *   import zio.http.codec.PathCodec.trailing
   *
   *   val register: URIO[TestClient, Unit] =
   *     TestClient.addRoutes(
   *       Method.GET / trailing          -> handler { Response.text("fallback") },
   *       Method.GET / "hello" / "world" -> handler { Response.text("Hey there!") },
   *     )
   *   }}}
   */
  def addRoutes[R](
    route: Route[R, Response],
    routes: Route[R, Response]*,
  ): ZIO[R with TestClient, Nothing, Unit] =
    ZIO.serviceWithZIO[TestClient](_.addRoutes(route, routes: _*))

  /**
   * Sets a fallback behaviour for when the TestClient attempts to access any
   * unspecified routes
   * @param fallbackHandler
   *   The function to execute when the client attempts to perform a request not
   *   part of its behavior
   * @tparam R
   *   Environment of the handler
   *
   * @example
   *   {{{
   *   import zio._
   *
   *   def record(seen: Ref[List[Request]]): URIO[TestClient, Unit] =
   *     TestClient.setFallbackHandler(req => seen.update(req :: _).as(Response.notFound))
   *   }}}
   */
  def setFallbackHandler[R](
    fallbackHandler: Request => ZIO[R, Response, Response],
  ): ZIO[R with TestClient, Nothing, Unit] =
    ZIO.serviceWithZIO[TestClient](_.setFallbackHandler(fallbackHandler))

  /** Sets the WebSocket app of the `TestClient` in the environment. */
  def installSocketApp(
    app: WebSocketApp[Any],
  ): ZIO[TestClient, Nothing, Unit] =
    ZIO.serviceWithZIO[TestClient](_.installSocketApp(app))

  /**
   * A layer that provides a `TestClient` and a `Client` that sends its requests
   * to it. The `TestClient` starts with no routes, so a request answers with
   * status 404 and logs a warning until routes are added.
   */
  val layer: ZLayer[Any, Nothing, TestClient & Client] =
    ZLayer.scopedEnvironment {
      for {
        behavior         <- Ref.make[Routes[Any, Response]](Routes.empty)
        socketBehavior   <- Ref.make[WebSocketApp[Any]](WebSocketApp.unit)
        fallbackBehavior <- Ref.make[Handler[Any, Response, Request, Response]](
          handler((req: Request) => ZIO.logWarning(s"Unexpected request route: ${req}").as(Response.notFound)),
        )
        driver = TestClient(behavior, socketBehavior, fallbackBehavior)
      } yield ZEnvironment[TestClient, Client](driver, ZClient.fromDriver(driver))
    }

  /**
   * Like `layer`, with the fallback handler set to `fallbackHandler`, which
   * runs with the environment `R` when a request matches no route.
   */
  def withFallbackHandler[R](
    fallbackHandler: Request => ZIO[R, Response, Response],
  ): ZLayer[R, Nothing, TestClient & Client] =
    ZLayer.environment[R] ++ layer >+> ZLayer.fromZIO(setFallbackHandler(fallbackHandler))
}
