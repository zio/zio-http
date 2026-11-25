package zio.http

import zio._

import zio.http.netty.NettyConfig
import zio.http.netty.server.NettyDriver

/**
 * Enables tests that make calls against "localhost" with user-specified
 * Behavior/Responses.
 *
 * @param driver
 *   The web driver that accepts our Server behavior
 * @param bindPort
 *   Port for HTTP interactions
 */
final case class TestServer(driver: Driver, bindPort: Int) extends Server {

  /**
   * Define 1-1 mappings between incoming Requests and outgoing Responses. A
   * request with the same method and path as `expectedRequest` gets `response`
   * when its URL matches and its headers include all headers of
   * `expectedRequest`; otherwise it gets a 404 response.
   *
   * @param expectedRequest
   *   Request that will trigger the provided Response
   * @param response
   *   Response that the Server will send
   *
   * @example
   *   {{{
   *   import zio._
   *
   *   def register(server: TestServer): UIO[Unit] =
   *     server.addRequestResponse(Request.get("/hello"), Response(Status.Ok))
   *   }}}
   */
  def addRequestResponse(
    expectedRequest: Request,
    response: Response,
  ): ZIO[Any, Nothing, Unit] = {
    addRoute(RoutePattern(expectedRequest.method, expectedRequest.path) -> handler { (realRequest: Request) =>
      if (
        // The way that the Client breaks apart and re-assembles the request prevents a straightforward
        //    expectedRequest == realRequest
        expectedRequest.url.relative == realRequest.url &&
        expectedRequest.method == realRequest.method &&
        expectedRequest.headers.forall(expectedHeader => realRequest.hasHeader(expectedHeader))
      ) response
      else Response.notFound
    })
  }

  /**
   * Adds a new route to the Server
   *
   * @param route
   *   New route
   * @example
   *   {{{
   *   import zio._
   *   import zio.http.codec.PathCodec.trailing
   *
   *   def register(server: TestServer, state: Ref[Int]): UIO[Unit] =
   *     server.addRoute(Method.ANY / trailing -> handler { (_: Path, _: Request) =>
   *       state.getAndUpdate(_ + 1).map { n =>
   *         if (n > 0) Response(Status.InternalServerError) else Response(Status.Ok)
   *       }
   *     })
   *   }}}
   */
  def addRoute[R](
    route: Route[R, Response],
  ): ZIO[R, Nothing, Unit] =
    for {
      r <- ZIO.environment[R]
      provided                      = route.provideEnvironment(r)
      routes: Routes[Any, Response] = provided.toRoutes
      _ <- driver.addApp(routes, r)
    } yield ()

  /**
   * Add new routes to the Server
   * @example
   *   {{{
   *   import zio._
   *   import zio.http.codec.PathCodec.trailing
   *
   *   def register(server: TestServer): UIO[Unit] =
   *     server.addRoutes(
   *       Routes(
   *         Method.ANY / trailing          -> handler { (_: Path, _: Request) => Response.text("Fallback handler") },
   *         Method.GET / "hello" / "world" -> handler { (_: Request) => Response.text("Hello world!") },
   *       ),
   *     )
   *   }}}
   */
  def addRoutes[R](
    routes: Routes[R, Response],
  ): ZIO[R, Nothing, Unit] =
    for {
      r <- ZIO.environment[R]
      provided: Routes[Any, Response] = routes.provideEnvironment(r)
      _ <- driver.addApp(provided, r)
    } yield ()

  override private[http] def installInternal[R](routes: Routes[R, Response])(implicit
    trace: zio.Trace,
    tag: EnvironmentTag[R],
  ): URIO[R, Unit] =
    ZIO
      .environment[R]
      .flatMap(
        driver.addApp(
          routes,
          _,
        ),
      )

  override def port: UIO[Int] = ZIO.succeed(bindPort)
}

/**
 * Accessors for [[TestServer]] and the layers that provide it.
 *
 * {{{
 * import zio._
 *
 * val program: ZIO[TestServer & Client, Throwable, String] =
 *   for {
 *     _        <- TestServer.addRequestResponse(Request.get("/hello"), Response.text("Hello"))
 *     port     <- ZIO.serviceWithZIO[TestServer](_.port)
 *     response <- Client.batched(Request.get(s"http://localhost:\$port/hello"))
 *     body     <- response.body.asString
 *   } yield body // "Hello"
 *
 * val result: Task[String] = program.provide(TestServer.default, Client.default)
 * }}}
 */
object TestServer {

  /** Adds the route to the `TestServer` in the environment. */
  def addRoute[R](
    route: Route[R, Response],
  ): ZIO[R with TestServer, Nothing, Unit] =
    ZIO.serviceWithZIO[TestServer](_.addRoute(route))

  /** Adds the routes to the `TestServer` in the environment. */
  def addRoutes[R](
    routes: Routes[R, Response],
  ): ZIO[R with TestServer, Nothing, Unit] =
    ZIO.serviceWithZIO[TestServer](_.addRoutes(routes))

  /**
   * Adds a 1-1 mapping between `request` and `response` to the `TestServer` in
   * the environment. See `TestServer#addRequestResponse` for how requests are
   * matched.
   */
  def addRequestResponse(
    request: Request,
    response: Response,
  ): ZIO[TestServer, Nothing, Unit] =
    ZIO.serviceWithZIO[TestServer](_.addRequestResponse(request, response))

  /**
   * A layer that starts the `Driver` from the environment and provides a
   * `TestServer` bound to the port that the driver reports.
   */
  val layer: ZLayer[Driver, Throwable, TestServer] =
    ZLayer.scoped {
      for {
        driver <- ZIO.service[Driver]
        result <- driver.start
      } yield TestServer(driver, result.port)
    }

  /**
   * A `TestServer` backed by a Netty driver that listens on any open port. The
   * Netty configuration is `NettyConfig.defaultWithFastShutdown`.
   */
  val default: ZLayer[Any, Nothing, TestServer] = ZLayer.make[TestServer][Nothing](
    TestServer.layer.orDie,
    ZLayer.succeed(Server.Config.default.onAnyOpenPort),
    NettyDriver.customized.orDie,
    ZLayer.succeed(NettyConfig.defaultWithFastShutdown),
  )

}
